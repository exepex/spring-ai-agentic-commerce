package io.github.exepex.commerce.servicenow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.exepex.commerce.servicenow.incidents.IncidentPoller;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The real server with Kafka in a container; one WireMock server stands in for both ServiceNow's Table API and the
 * governance API (their paths do not overlap). The incident agent connects with a real MCP client. Every scenario
 * group extends this class, so they all run against the same server and the same WireMock stubs, reset before each
 * test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"commerce.servicenow.username=agent.user", "commerce.servicenow.password=secret",
                "commerce.servicenow.poll-interval=1h"})
@Import(TestcontainersConfiguration.class)
abstract class ServiceNowMcpServerIntegrationTestSupport {

    /** Work notes as a real instance shows them: newest first, in the integration user's time zone. */
    static final String WORK_NOTES_SHOWN = """
            2026-10-02 02:10:00 - Trailhead Agent (Work notes)
            Refunded EUR 39.50.

            2026-10-02 02:05:00 - Ana Desk (Work notes)
            Asked the warehouse.

            They will call back.

            """;

    static final String AGENT_TOKEN = "dev-incident-agent-token";
    static final String AGENT_USER = "a1b2c3d4agentuser";
    static final String LINKED_ORDER = "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c";
    static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final Map<String, String> STATE_NAMES = Map.of("1", "New", "2", "In Progress", "3", "On Hold",
            "6", "Resolved", "7", "Closed");
    static final WireMockServer SERVICES = startWireMock();

    @LocalServerPort
    int port;

    @Autowired
    IncidentPoller poller;

    @Autowired
    private KafkaContainer kafka;

    private McpSyncClient agent;

    @DynamicPropertySource
    static void pointAtWireMock(DynamicPropertyRegistry registry) {
        registry.add("commerce.servicenow.instance-url", SERVICES::baseUrl);
        registry.add("spring.http.serviceclient.governance.base-url", SERVICES::baseUrl);
    }

    @BeforeEach
    void stubTheServices() {
        SERVICES.resetAll();
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/sys_user"))
                .willReturn(okJson("{\"result\": [{\"sys_id\": \"" + AGENT_USER + "\"}]}")));
        stubJournal("""
                {"result": [{
                  "work_notes": "%s",
                  "comments": "2026-10-02 02:00:00 - Ana Desk (Additional comments)\\nThe customer says order 6f0c is late.\\n\\n"}]}"""
                .formatted(WORK_NOTES_SHOWN.replace("\n", "\\n")));
        SERVICES.stubFor(patch(urlPathEqualTo("/api/now/table/incident/sys-1")).willReturn(okJson("{\"result\": {}}")));
        SERVICES.stubFor(get("/api/agent-switches").willReturn(okJson("{\"incident-agent\": true}")));
        SERVICES.stubFor(post("/api/agent/tool-calls").willReturn(aResponse().withStatus(200)));
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okJson("[]")));
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okJson("[]")));
        SERVICES.stubFor(post(urlPathMatching("/api/agent/cases/.*")).willReturn(aResponse().withStatus(200)));
        agent = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + AGENT_TOKEN))
                        .build())
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        agent.initialize();
    }

    @AfterEach
    void disconnect() {
        agent.closeGracefully();
    }

    void stubJournal(String body) {
        stubJournal("sys-1", body);
    }

    /** The incident's journal fields as a real instance shows them with sysparm_display_value=true. */
    void stubJournal(String sysId, String body) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("sys_id=" + sysId))
                .withQueryParam("sysparm_display_value", equalTo("true"))
                .willReturn(okJson(body)));
    }

    void stubIncident(String assignedTo, String state) {
        stubIncident(assignedTo, state, Instant.now());
    }

    void stubIncident(String assignedTo, String state, Instant updatedAt) {
        stubIncident("INC0010001", "sys-1", state, "Online Shop Agent", assignedTo, "", updatedAt);
    }

    /**
     * The incident, found by its number, as the tools and the claim find it, and by its link, as a case finds it on its
     * own or in a read-back of that one case.
     */
    void stubIncident(String number, String sysId, String state, String group, String assignedTo, String caseId,
            Instant updatedAt) {
        String found = "{\"result\": [" + incidentRow(number, sysId, state, group, assignedTo, caseId, updatedAt) + "]}";
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("number=" + number))
                .willReturn(okJson(found)));
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("sys_id=" + sysId))
                .withQueryParam("sysparm_display_value", equalTo("all"))
                .willReturn(okJson(found)));
        stubLinkedIncidents(List.of(sysId), found);
    }

    /** The incidents a read-back of several cases asks ServiceNow for together, by their sys_ids in that order. */
    void stubLinkedIncidents(List<String> sysIds, String found) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("sys_idIN" + String.join(",", sysIds)))
                .withQueryParam("sysparm_display_value", equalTo("all"))
                .willReturn(okJson(found)));
    }

    /**
     * The shop's cases as it lists them, each linked to its incident on this instance the way the poller links it. The
     * tests number a case's incident INC00100NN and give it the sys_id sys-NN.
     */
    static ResponseDefinitionBuilder okCases(String cases) {
        return okJson(Pattern.compile("\"incidentNumber\": \"INC00100(\\d\\d)\"").matcher(cases).replaceAll(number ->
                Matcher.quoteReplacement(number.group() + ", \"incidentUrl\": \"" + SERVICES.baseUrl()
                        + "/incident.do?sys_id=sys-" + Integer.parseInt(number.group(1)) + "\"")));
    }

    static String incidentRow(String number, String sysId, String state, String group, String assignedTo,
            String caseId, Instant updatedAt) {
        return """
                {"sys_id": {"value": "%s"}, "number": {"value": "%s"},
                 "short_description": {"value": "Order arrived broken"}, "description": {"value": "See comments"},
                 "state": {"value": "%s", "display_value": "%s"},
                 "assignment_group": {"display_value": "%s"},
                 "assigned_to": {"value": "%s", "display_value": "Incident Agent"},
                 "caller_id": {"display_value": "Ada Lovelace"}, "correlation_id": {"value": "%s"},
                 "correlation_display": {"value": "%s"}, "sys_created_on": {"value": "2026-10-02 08:55:00"},
                 "sys_updated_on": {"value": "%s"}}"""
                .formatted(sysId, number, state, STATE_NAMES.getOrDefault(state, state), group, assignedTo, LINKED_ORDER,
                        caseId, SERVICENOW_TIME.format(updatedAt));
    }

    void stubNewIncidents(String rows) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", containing("assigned_toISEMPTY"))
                .willReturn(okJson("{\"result\": " + rows + "}")));
    }

    void stubServiceDeskIncidents(String rows) {
        stubServiceDeskIncidents("0", rows);
    }

    void stubServiceDeskIncidents(String offset, String rows) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("correlation_idISNOTEMPTY^stateNOT IN6,7,8^ORDERBYsys_created_on"))
                .withQueryParam("sysparm_offset", equalTo(offset))
                .willReturn(okJson("{\"result\": " + rows + "}")));
    }

    void stubClaimed(String rows) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query",
                        equalTo("assignment_group.name=Online Shop Agent^assigned_to=" + AGENT_USER + "^state=2"))
                .willReturn(okJson("{\"result\": " + rows + "}")));
    }

    McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
        return agent.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    List<String> incidentEvents() {
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "servicenow-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("servicenow.incidents"));
            List<String> events = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (events.isEmpty() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))) {
                    events.add(record.value());
                }
            }
            return events;
        }
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }
}
