package io.github.exepex.commerce.servicenow;

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
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The shop's cases are sent to ServiceNow: each new case opens one incident, and the notes added to it later become
 * work notes, never twice.
 */
class CaseSendingIntegrationTest extends ServiceNowMcpServerIntegrationTestSupport {

    @Test
    void sendsANewCaseAsAnIncidentInTheAgentGroupAndItsNotesAsWorkNotes() {
        String caseId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
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
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
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
    void aNoteFarBackInALongJournalIsStillFoundAndNotAddedAgain() {
        String caseId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
                [{"supportCase": {"id": "%s", "type": "STOCK_OUT", "status": "WITH_AGENT", "incidentNumber": "INC0010009"},
                  "unsentNotes": [{"id": "%s", "text": "Raised again by the catalog."}]}]"""
                .formatted(caseId, noteId))));
        stubIncident("INC0010009", "sys-9", "2", "Online Shop Agent", AGENT_USER, caseId, Instant.now());
        // Much was written after the note, so its marker lies beyond what get_incident shows the agent.
        String newer = "2026-10-02 03:00:00 - Ana Desk (Work notes)\\n" + "x".repeat(25_000) + "\\n\\n";
        stubJournal("sys-9", """
                {"result": [{"work_notes": "%s2026-10-02 02:05:00 - Trailhead Agent (Work notes)\\nRaised again by the catalog.\\n\\n[shop note %s]\\n\\n",
                             "comments": ""}]}""".formatted(newer, noteId));

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-9")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/notes/" + noteId + "/sent")));
    }

    @Test
    void aNoteAtTheLongestLengthIsShortenedSoItsMarkerFits() {
        String caseId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
                [{"supportCase": {"id": "%s", "type": "STOCK_OUT", "status": "WITH_AGENT", "incidentNumber": "INC0010009"},
                  "unsentNotes": [{"id": "%s", "text": "%s"}]}]"""
                .formatted(caseId, noteId, "y".repeat(4_000)))));
        stubIncident("INC0010009", "sys-9", "2", "Online Shop Agent", AGENT_USER, caseId, Instant.now());
        stubJournal("sys-9", "{\"result\": [{\"work_notes\": \"\", \"comments\": \"\"}]}");
        SERVICES.stubFor(patch(urlPathEqualTo("/api/now/table/incident/sys-9")).willReturn(okJson("{\"result\": {}}")));

        poller.poll();

        String workNote = JsonPath.read(SERVICES.findAll(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-9")))
                .getFirst().getBodyAsString(), "$.work_notes");
        assertThat(workNote).hasSize(4_000).endsWith("\n\n[shop note " + noteId + "]").startsWith("yyy");
    }

    @Test
    void aCaseWhoseIncidentWasOpenedBeforeIsLinkedAndNotOpenedAgain() {
        String caseId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
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
    void aCaseForPeopleGoesStraightToTheDefaultTeam() {
        String caseId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
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
    void notesForAnIncidentAlreadyResolvedAreNotSentThereButOnesItAlreadyHoldsAreMarkedSent() {
        String caseId = UUID.randomUUID().toString();
        String appliedNoteId = UUID.randomUUID().toString();
        String newNoteId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
                [{"supportCase": {"id": "%s", "type": "HANDOFF", "status": "WITH_AGENT", "incidentNumber": "INC0010015"},
                  "unsentNotes": [{"id": "%s", "text": "The customer wrote."}, {"id": "%s", "text": "The customer called again."}]}]"""
                .formatted(caseId, appliedNoteId, newNoteId))));
        stubIncident("INC0010015", "sys-15", "6", "Online Shop Agent", "", caseId, Instant.now());
        // ServiceNow applied the first note, but its answer never arrived; then a person resolved the incident.
        stubJournal("sys-15", """
                {"result": [{"work_notes": "2026-10-02 02:05:00 - Trailhead Agent (Work notes)\\nThe customer wrote.\\n\\n[shop note %s]\\n\\n",
                             "comments": ""}]}""".formatted(appliedNoteId));

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-15")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/notes/" + appliedNoteId + "/sent")));
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/notes/" + newNoteId + "/sent")));
    }
}
