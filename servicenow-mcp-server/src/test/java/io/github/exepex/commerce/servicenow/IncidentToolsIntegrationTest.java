package io.github.exepex.commerce.servicenow;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** The incident agent works an incident through the MCP tools: only one it owns, and only within its rules. */
class IncidentToolsIntegrationTest extends ServiceNowMcpServerIntegrationTestSupport {

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
    void handingACasesIncidentToATeamTellsTheShopAtOnce() {
        String caseId = UUID.randomUUID().toString();
        stubIncident("INC0010001", "sys-1", "2", "Online Shop Agent", AGENT_USER, caseId, Instant.now());

        call("assign_to_team", Map.of("number", "INC0010001", "team", "fulfilment", "note", "Arrange a new delivery."));

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/incident-state"))
                .withHeader("Authorization", equalTo("Bearer " + AGENT_TOKEN))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010001", "status": "WITH_TEAM", "assignmentGroup": "Fulfilment",
                         "incidentFinal": false,
                         "orderId": "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c"}""")));
    }
}
