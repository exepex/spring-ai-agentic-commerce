package io.github.exepex.commerce.servicenow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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

    /** Work notes as a real instance shows them: newest first, in the integration user's time zone. */
    private static final String WORK_NOTES_SHOWN = """
            2026-10-02 02:10:00 - Trailhead Agent (Work notes)
            Refunded EUR 39.50.

            2026-10-02 02:05:00 - Ana Desk (Work notes)
            Asked the warehouse.

            They will call back.

            """;

    private static final String AGENT_TOKEN = "dev-incident-agent-token";
    private static final String AGENT_USER = "a1b2c3d4agentuser";
    private static final String LINKED_ORDER = "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c";
    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final Map<String, String> STATE_NAMES = Map.of("1", "New", "2", "In Progress", "3", "On Hold",
            "6", "Resolved", "7", "Closed");
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
        assertThat((String) JsonPath.read(incident, "$.linkedOrderId")).isEqualTo(LINKED_ORDER);
        assertThat((String) JsonPath.read(incident, "$.openedAt")).isEqualTo("2026-10-02T08:55:00Z");
        assertThat((String) JsonPath.read(incident, "$.workNotes")).isEqualTo(WORK_NOTES_SHOWN.strip());
        assertThat((String) JsonPath.read(incident, "$.comments"))
                .isEqualTo("2026-10-02 02:00:00 - Ana Desk (Additional comments)\nThe customer says order 6f0c is late.");
        assertThat(call("get_incident", Map.of("number", "INC1^ORactive=true")).isError()).isTrue();
    }

    @Test
    void aLongJournalIsCutToItsNewestPart() {
        stubIncident(AGENT_USER, "2");
        String newest = "2026-10-02 02:05:00 - Ana Desk (Work notes)\\nThe newest note.\\n\\n";
        String older = "2026-10-01 02:05:00 - Ana Desk (Work notes)\\n" + "x".repeat(30_000) + "\\n\\n";
        stubJournal("{\"result\": [{\"work_notes\": \"" + newest + older + "\", \"comments\": \"\"}]}");

        String workNotes = JsonPath.read(text(call("get_incident", Map.of("number", "INC0010001"))), "$.workNotes");

        assertThat(workNotes).startsWith("2026-10-02 02:05:00 - Ana Desk (Work notes)\nThe newest note.")
                .endsWith("\n[Older entries left out.]")
                .hasSizeLessThan(20_100);
    }

    @Test
    void theAgentReadsAndChangesOnlyAnIncidentItIsWorking() {
        stubIncident("someone-else", "2");

        McpSchema.CallToolResult read = call("get_incident", Map.of("number", "INC0010001"));
        McpSchema.CallToolResult refused = call("add_work_note", Map.of("number", "INC0010001", "note", "Checked the order."));

        assertThat(read.isError()).isTrue();
        assertThat(text(read)).contains("not yours to change");
        assertThat(refused.isError()).isTrue();
        assertThat(text(refused)).contains("not yours to change");
        SERVICES.verify(0, getRequestedFor(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("sys_id=sys-1")));
        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/tool-calls"))
                .withHeader("Authorization", equalTo("Bearer " + AGENT_TOKEN))
                .withRequestBody(matchingJsonPath("$.outcome", equalTo("DENIED"))));
    }

    @Test
    void anIncidentAPersonMovedToAnotherGroupIsNoLongerTheAgentsEvenWhileStillAssignedToIt() {
        stubIncident("INC0010001", "sys-1", "2", "Payments", AGENT_USER, "", Instant.now());

        McpSchema.CallToolResult read = call("get_incident", Map.of("number", "INC0010001"));
        McpSchema.CallToolResult resolve = call("resolve_incident", Map.of("number", "INC0010001", "resolution", "Done."));

        assertThat(read.isError()).isTrue();
        assertThat(text(read)).contains("not yours to change");
        assertThat(resolve.isError()).isTrue();
        assertThat(text(resolve)).contains("not yours to change");
        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
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
    void aHandOffWithoutATeamGoesToTheDefaultTeam() {
        stubIncident(AGENT_USER, "2");

        McpSchema.CallToolResult handed = call("assign_to_team", Map.of("number", "INC0010001",
                "note", "The incident agent failed, so a person must finish this."));

        assertThat(handed.isError()).isFalse();
        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withRequestBody(matchingJsonPath("$.assignment_group", equalTo("Customer Care"))));
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
        stubIncident("", "1");

        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withQueryParam("sysparm_input_display_value", equalTo("false"))
                .withRequestBody(matchingJsonPath("$.assigned_to", equalTo(AGENT_USER)))
                .withRequestBody(matchingJsonPath("$.state", equalTo("2"))));
        assertThat(incidentEvents()).anySatisfy(event -> {
            assertThat((String) JsonPath.read(event, "$.number")).isEqualTo("INC0010001");
            assertThat((String) JsonPath.read(event, "$.orderId")).isEqualTo(LINKED_ORDER);
        });
    }

    @Test
    void anIncidentAPersonTookBeforeTheClaimIsLeftToThem() {
        stubNewIncidents("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "1"},
                  "assigned_to": {"value": ""}}]""");
        stubClaimed("[]");
        stubIncident("desk.ana", "2");

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
    }

    @Test
    void anIncidentMovedToAnotherGroupBeforeTheClaimIsLeftThere() {
        stubNewIncidents("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "1"},
                  "assignment_group": {"display_value": "Online Shop Agent"}, "assigned_to": {"value": ""}}]""");
        stubClaimed("[]");
        stubIncident("INC0010001", "sys-1", "1", "Payments", "", "", Instant.now());

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
    }

    @Test
    void aClaimTheAgentDidNotFinishGoesToTheDefaultTeam() {
        stubNewIncidents("[]");
        stubClaimed("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "2"},
                  "assignment_group": {"display_value": "Online Shop Agent"},
                  "assigned_to": {"value": "%s"}, "sys_updated_on": {"value": "%s"}}]"""
                .formatted(AGENT_USER, SERVICENOW_TIME.format(Instant.now().minus(Duration.ofHours(1)))));
        stubIncident(AGENT_USER, "2", Instant.now().minus(Duration.ofHours(1)));

        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withQueryParam("sysparm_input_display_value", equalTo("true"))
                .withRequestBody(matchingJsonPath("$.assignment_group", equalTo("Customer Care")))
                .withRequestBody(matchingJsonPath("$.work_notes", containing("did not finish"))));
    }

    @Test
    void aClaimTheAgentWorkedOnSinceTheListingIsNotHandedOver() {
        stubNewIncidents("[]");
        stubClaimed("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "2"},
                  "assignment_group": {"display_value": "Online Shop Agent"},
                  "assigned_to": {"value": "%s"}, "sys_updated_on": {"value": "%s"}}]"""
                .formatted(AGENT_USER, SERVICENOW_TIME.format(Instant.now().minus(Duration.ofHours(1)))));
        stubIncident(AGENT_USER, "2", Instant.now());

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
    }

    @Test
    void sendsANewCaseAsAnIncidentInTheAgentGroupAndItsNotesAsWorkNotes() {
        String caseId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okJson("""
                [{"supportCase": {"id": "%s", "orderId": "%s", "type": "STOCK_OUT", "status": "PENDING",
                  "title": "[STOCK_OUT] Order 6f0c2b8e can no longer be fulfilled", "description": "Water damage.",
                  "raisedBy": "catalog-service", "incidentNumber": null, "createdAt": "2026-10-02T09:15:00Z"},
                  "unsentNotes": [{"id": "%s", "text": "Raised again by the catalog."}]}]"""
                .formatted(caseId, LINKED_ORDER, noteId))));
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("correlation_display=" + caseId))
                .willReturn(okJson("{\"result\": []}")));
        SERVICES.stubFor(post(urlPathEqualTo("/api/now/table/incident")).willReturn(okJson("{\"result\": "
                + incidentRow("INC0010009", "sys-9", "1", "Online Shop Agent", "", caseId, Instant.now()) + "}")));
        stubIncident("INC0010009", "sys-9", "1", "Online Shop Agent", "", caseId, Instant.now());
        SERVICES.stubFor(patch(urlPathEqualTo("/api/now/table/incident/sys-9")).willReturn(okJson("{\"result\": {}}")));
        stubJournal("sys-9", "{\"result\": [{\"work_notes\": \"\", \"comments\": \"\"}]}");

        poller.poll();

        SERVICES.verify(postRequestedFor(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_input_display_value", equalTo("true"))
                .withRequestBody(equalToJson("""
                        {"assignment_group": "Online Shop Agent",
                         "short_description": "[STOCK_OUT] Order 6f0c2b8e can no longer be fulfilled",
                         "description": "Water damage.", "correlation_id": "%s", "correlation_display": "%s"}"""
                        .formatted(LINKED_ORDER, caseId))));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/incident"))
                .withHeader("Authorization", equalTo("Bearer " + AGENT_TOKEN))
                .withRequestBody(matchingJsonPath("$.number", equalTo("INC0010009")))
                .withRequestBody(matchingJsonPath("$.url", equalTo(SERVICES.baseUrl() + "/incident.do?sys_id=sys-9"))));
        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-9"))
                .withRequestBody(equalToJson("{\"work_notes\": \"Raised again by the catalog.\\n\\n[shop note " + noteId + "]\"}")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/notes/" + noteId + "/sent")));
    }

    @Test
    void aNoteTheIncidentAlreadyHoldsIsMarkedSentWithoutBeingAddedAgain() {
        String caseId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okJson("""
                [{"supportCase": {"id": "%s", "type": "STOCK_OUT", "status": "WITH_AGENT", "incidentNumber": "INC0010009"},
                  "unsentNotes": [{"id": "%s", "text": "Raised again by the catalog."}]}]"""
                .formatted(caseId, noteId))));
        stubIncident("INC0010009", "sys-9", "2", "Online Shop Agent", AGENT_USER, caseId, Instant.now());
        // ServiceNow applied the note earlier, but its answer never arrived, so the shop still holds it as unsent.
        stubJournal("sys-9", """
                {"result": [{"work_notes": "2026-10-02 02:05:00 - Trailhead Agent (Work notes)\\nRaised again by the catalog.\\n\\n[shop note %s]\\n\\n",
                             "comments": ""}]}""".formatted(noteId));

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-9")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/notes/" + noteId + "/sent")));
    }

    @Test
    void aCaseWhoseIncidentWasOpenedBeforeIsLinkedAndNotOpenedAgain() {
        String caseId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okJson("""
                [{"supportCase": {"id": "%s", "orderId": null, "type": "HANDOFF", "status": "PENDING",
                  "title": "[HANDOFF] A request needs a person", "description": "Help."}, "unsentNotes": []}]"""
                .formatted(caseId))));
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("correlation_display=" + caseId))
                .willReturn(okJson("{\"result\": [" + incidentRow("INC0010008", "sys-8", "2", "Online Shop Agent",
                        AGENT_USER, caseId, Instant.now()) + "]}")));

        poller.poll();

        SERVICES.verify(0, postRequestedFor(urlPathEqualTo("/api/now/table/incident")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/incident"))
                .withRequestBody(matchingJsonPath("$.number", equalTo("INC0010008"))));
    }

    @Test
    void readsBackWhoHasEachCasesIncident() {
        String withAgent = UUID.randomUUID().toString();
        String withTeam = UUID.randomUUID().toString();
        String resolved = UUID.randomUUID().toString();
        String takenByAPerson = UUID.randomUUID().toString();
        String onHold = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okJson("""
                [{"id": "%s", "incidentNumber": "INC0010011"}, {"id": "%s", "incidentNumber": "INC0010012"},
                 {"id": "%s", "incidentNumber": "INC0010013"}, {"id": "%s", "incidentNumber": "INC0010014"},
                 {"id": "%s", "incidentNumber": "INC0010016"}]"""
                .formatted(withAgent, withTeam, resolved, takenByAPerson, onHold))));
        stubIncident("INC0010011", "sys-11", "2", "Online Shop Agent", AGENT_USER, withAgent, Instant.now());
        stubIncident("INC0010012", "sys-12", "2", "Payments", "", withTeam, Instant.now());
        stubIncident("INC0010013", "sys-13", "7", "Payments", "", resolved, Instant.now());
        stubIncident("INC0010014", "sys-14", "2", "Online Shop Agent", "desk-ana", takenByAPerson, Instant.now());
        stubIncident("INC0010016", "sys-16", "3", "Online Shop Agent", "", onHold, Instant.now());

        poller.poll();

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + withAgent + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010011", "status": "WITH_AGENT", "assignmentGroup": "Online Shop Agent"}""")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + withTeam + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010012", "status": "WITH_TEAM", "assignmentGroup": "Payments"}""")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + resolved + "/incident-state"))
                .withRequestBody(matchingJsonPath("$.status", equalTo("RESOLVED"))));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + takenByAPerson + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010014", "status": "WITH_TEAM",
                         "assignmentGroup": "Online Shop Agent (Incident Agent)"}""")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + onHold + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010016", "status": "WITH_TEAM", "assignmentGroup": "Online Shop Agent (On Hold)"}""")));
    }

    @Test
    void aCaseForPeopleGoesStraightToTheDefaultTeam() {
        String caseId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okJson("""
                [{"supportCase": {"id": "%s", "type": "HANDOFF", "status": "PENDING", "title": "[HANDOFF] A request needs a person",
                  "description": "Carried over from the escalation queue.", "forPeople": true}, "unsentNotes": []}]"""
                .formatted(caseId))));
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("correlation_display=" + caseId))
                .willReturn(okJson("{\"result\": []}")));
        SERVICES.stubFor(post(urlPathEqualTo("/api/now/table/incident")).willReturn(okJson("{\"result\": "
                + incidentRow("INC0010017", "sys-17", "1", "Customer Care", "", caseId, Instant.now()) + "}")));

        poller.poll();

        SERVICES.verify(postRequestedFor(urlPathEqualTo("/api/now/table/incident"))
                .withRequestBody(matchingJsonPath("$.assignment_group", equalTo("Customer Care"))));
    }

    @Test
    void notesForAnIncidentAlreadyResolvedAreNotSentThere() {
        String caseId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okJson("""
                [{"supportCase": {"id": "%s", "type": "HANDOFF", "status": "WITH_AGENT", "incidentNumber": "INC0010015"},
                  "unsentNotes": [{"id": "%s", "text": "The customer called again."}]}]"""
                .formatted(caseId, UUID.randomUUID()))));
        stubIncident("INC0010015", "sys-15", "6", "Online Shop Agent", "", caseId, Instant.now());

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-15")));
        SERVICES.verify(0, postRequestedFor(urlPathMatching("/api/agent/cases/.*/notes/.*/sent")));
    }

    @Test
    void handingACasesIncidentToATeamTellsTheShopAtOnce() {
        String caseId = UUID.randomUUID().toString();
        stubIncident("INC0010001", "sys-1", "2", "Online Shop Agent", AGENT_USER, caseId, Instant.now());

        call("assign_to_team", Map.of("number", "INC0010001", "team", "fulfilment", "note", "Arrange a new delivery."));

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/incident-state"))
                .withHeader("Authorization", equalTo("Bearer " + AGENT_TOKEN))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010001", "status": "WITH_TEAM", "assignmentGroup": "Fulfilment"}""")));
    }

    private void stubJournal(String body) {
        stubJournal("sys-1", body);
    }

    /** The incident's journal fields as a real instance shows them with sysparm_display_value=true. */
    private void stubJournal(String sysId, String body) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("sys_id=" + sysId))
                .withQueryParam("sysparm_display_value", equalTo("true"))
                .willReturn(okJson(body)));
    }

    private void stubIncident(String assignedTo, String state) {
        stubIncident(assignedTo, state, Instant.now());
    }

    private void stubIncident(String assignedTo, String state, Instant updatedAt) {
        stubIncident("INC0010001", "sys-1", state, "Online Shop Agent", assignedTo, "", updatedAt);
    }

    private void stubIncident(String number, String sysId, String state, String group, String assignedTo, String caseId,
            Instant updatedAt) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("number=" + number))
                .willReturn(okJson("{\"result\": [" + incidentRow(number, sysId, state, group, assignedTo, caseId, updatedAt)
                        + "]}")));
    }

    private static String incidentRow(String number, String sysId, String state, String group, String assignedTo,
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

    private void stubNewIncidents(String rows) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", containing("assigned_toISEMPTY"))
                .willReturn(okJson("{\"result\": " + rows + "}")));
    }

    private void stubClaimed(String rows) {
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query",
                        equalTo("assignment_group.name=Online Shop Agent^assigned_to=" + AGENT_USER + "^state=2"))
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
