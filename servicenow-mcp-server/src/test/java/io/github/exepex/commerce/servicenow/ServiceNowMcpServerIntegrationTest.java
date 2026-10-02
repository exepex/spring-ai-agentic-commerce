package io.github.exepex.commerce.servicenow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The real server with Kafka in a container; one WireMock server stands in for both ServiceNow's Table API and the
 * governance API (their paths do not overlap). The incident agent connects with a real MCP client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"commerce.servicenow.username=agent.user", "commerce.servicenow.password=secret",
                "commerce.servicenow.poll-interval=1h"})
@Import(TestcontainersConfiguration.class)
class ServiceNowMcpServerIntegrationTest {

    private static final String AGENT_TOKEN = "dev-incident-agent-token";
    private static final String AGENT_USER = "a1b2c3d4agentuser";
    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final WireMockServer SERVICES = startWireMock();

    @LocalServerPort
    private int port;

    @Autowired
    private IncidentPoller poller;

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
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/sys_journal_field")).willReturn(okJson("""
                {"result": [{"sys_created_on": "2026-10-02 09:00:00", "sys_created_by": "desk.ana", "element": "comments",
                             "value": "The customer says order 6f0c is late."}]}""")));
        SERVICES.stubFor(patch(urlPathEqualTo("/api/now/table/incident/sys-1")).willReturn(okJson("{\"result\": {}}")));
        SERVICES.stubFor(get("/api/agent-switches").willReturn(okJson("{\"incident-agent\": true}")));
        SERVICES.stubFor(post("/api/agent/tool-calls").willReturn(aResponse().withStatus(200)));
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

    @Test
    void refusesACallerWithoutAnAgentToken() {
        int status = RestClient.create("http://localhost:" + port).post().uri("/mcp")
                .header("Content-Type", "application/json").body("{}")
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(401);
    }

    @Test
    void readsAnIncidentWithItsNotes() {
        stubIncident(AGENT_USER, "2");

        String incident = text(call("get_incident", Map.of("number", "INC0010001")));

        assertThat((String) JsonPath.read(incident, "$.shortDescription")).isEqualTo("Order arrived broken");
        assertThat((String) JsonPath.read(incident, "$.notes[0].text")).isEqualTo("The customer says order 6f0c is late.");
        assertThat(call("get_incident", Map.of("number", "INC1^ORactive=true")).isError()).isTrue();
    }

    @Test
    void theAgentChangesOnlyAnIncidentItIsWorking() {
        stubIncident("someone-else", "2");

        McpSchema.CallToolResult refused = call("add_work_note", Map.of("number", "INC0010001", "note", "Checked the order."));

        assertThat(refused.isError()).isTrue();
        assertThat(text(refused)).contains("not yours to change");
        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/tool-calls"))
                .withHeader("Authorization", equalTo("Bearer " + AGENT_TOKEN))
                .withRequestBody(matchingJsonPath("$.outcome", equalTo("DENIED"))));
    }

    @Test
    void handsAnIncidentToTheTeamByItsGroupAndRecordsIt() {
        stubIncident(AGENT_USER, "2");

        McpSchema.CallToolResult handed = call("assign_to_team", Map.of("number", "INC0010001", "team", "payments",
                "note", "The refund failed at the card processor; please refund the customer another way."));

        assertThat(handed.isError()).isFalse();
        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withQueryParam("sysparm_input_display_value", equalTo("true"))
                .withRequestBody(equalToJson("""
                        {"assignment_group": "Payments", "assigned_to": "",
                         "work_notes": "The refund failed at the card processor; please refund the customer another way."}""")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/tool-calls"))
                .withRequestBody(matchingJsonPath("$.action", equalTo("servicenow:assign_to_team")))
                .withRequestBody(matchingJsonPath("$.outcome", equalTo("SUCCEEDED"))));
        assertThat(call("assign_to_team", Map.of("number", "INC0010001", "team", "legal", "note", "x")).isError()).isTrue();
    }

    @Test
    void resolvesWithTheStoredStateCodeAndCloseCode() {
        stubIncident(AGENT_USER, "2");

        call("resolve_incident", Map.of("number", "INC0010001", "resolution", "Refunded the broken item."));

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withQueryParam("sysparm_input_display_value", equalTo("false"))
                .withRequestBody(equalToJson("""
                        {"state": "6", "close_code": "Solution provided", "close_notes": "Refunded the broken item."}""")));
    }

    @Test
    void aSwitchedOffAgentCanOnlyHandItsIncidentToATeam() {
        stubIncident(AGENT_USER, "2");
        SERVICES.stubFor(get("/api/agent-switches").willReturn(okJson("{\"incident-agent\": false}")));

        McpSchema.CallToolResult note = call("add_work_note", Map.of("number", "INC0010001", "note", "Checked it."));
        McpSchema.CallToolResult handed = call("assign_to_team", Map.of("number", "INC0010001", "team", "customer-care",
                "note", "The incident agent is switched off."));

        assertThat(note.isError()).isTrue();
        assertThat(text(note)).contains("switched off");
        assertThat(handed.isError()).isFalse();
        SERVICES.verify(1, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
    }

    @Test
    void claimsANewIncidentOnceAndAnnouncesIt() {
        stubNewIncidents("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"},
                  "short_description": {"value": "Order arrived broken"}, "state": {"value": "1", "display_value": "New"},
                  "assignment_group": {"display_value": "Online Shop Agent"}, "assigned_to": {"value": ""}}]""");
        stubClaimed("[]");

        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withQueryParam("sysparm_input_display_value", equalTo("false"))
                .withRequestBody(matchingJsonPath("$.assigned_to", equalTo(AGENT_USER)))
                .withRequestBody(matchingJsonPath("$.state", equalTo("2"))));
        assertThat(incidentEvents()).anySatisfy(event ->
                assertThat((String) JsonPath.read(event, "$.number")).isEqualTo("INC0010001"));
    }

    @Test
    void aClaimTheAgentDidNotFinishGoesToTheDefaultTeam() {
        stubNewIncidents("[]");
        stubClaimed("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "2"},
                  "assigned_to": {"value": "%s"}, "sys_updated_on": {"value": "%s"}}]"""
                .formatted(AGENT_USER, SERVICENOW_TIME.format(Instant.now().minus(Duration.ofHours(1)))));

        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withQueryParam("sysparm_input_display_value", equalTo("true"))
                .withRequestBody(matchingJsonPath("$.assignment_group", equalTo("Customer Care")))
                .withRequestBody(matchingJsonPath("$.work_notes", containing("did not finish"))));
    }

    private void stubIncident(String assignedTo, String state) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("number=INC0010001"))
                .willReturn(okJson("""
                        {"result": [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"},
                          "short_description": {"value": "Order arrived broken"}, "description": {"value": "See comments"},
                          "state": {"value": "%s", "display_value": "In Progress"},
                          "assignment_group": {"display_value": "Online Shop Agent"},
                          "assigned_to": {"value": "%s", "display_value": "Incident Agent"},
                          "caller_id": {"display_value": "Ada Lovelace"}, "sys_updated_on": {"value": "2026-10-02 09:00:00"}}]}"""
                        .formatted(state, assignedTo))));
    }

    private void stubNewIncidents(String rows) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", containing("assigned_toISEMPTY"))
                .willReturn(okJson("{\"result\": " + rows + "}")));
    }

    private void stubClaimed(String rows) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("assigned_to=" + AGENT_USER + "^state=2"))
                .willReturn(okJson("{\"result\": " + rows + "}")));
    }

    private McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
        return agent.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    private List<String> incidentEvents() {
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
