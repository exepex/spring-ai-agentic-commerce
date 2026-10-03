package io.github.exepex.commerce.servicenow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The poller reads back who has each case's incident and tells the shop, reporting a resolution only once the case's
 * notes are settled.
 */
class CaseReadBackIntegrationTest extends ServiceNowMcpServerIntegrationTestSupport {

    @Test
    void aCaseWhoseIncidentIsOnAnotherInstanceIsLeftAloneThoughThisInstanceHasAnIncidentWithItsNumber() {
        String caseId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        // Opened on an earlier simulator run, which numbered its incidents from INC0010001 too.
        String elsewhere = """
                {"id": "%s", "type": "HANDOFF", "status": "WITH_AGENT", "incidentNumber": "INC0010030",
                 "incidentUrl": "http://localhost:1/incident.do?sys_id=sys-30"}""".formatted(caseId);
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okJson("""
                [{"supportCase": %s, "unsentNotes": [{"id": "%s", "text": "The customer called again."}]}]"""
                .formatted(elsewhere, noteId))));
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okJson("[" + elsewhere + "]")));
        stubIncident("INC0010030", "sys-31", "2", "Payments", "", "", Instant.now());
        stubJournal("sys-31", "{\"result\": [{\"work_notes\": \"\", \"comments\": \"\"}]}");
        SERVICES.stubFor(patch(urlPathEqualTo("/api/now/table/incident/sys-31")).willReturn(okJson("{\"result\": {}}")));

        poller.poll();

        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/incident-state")));
        SERVICES.verify(0, postRequestedFor(urlPathMatching("/api/agent/cases/" + caseId + "/notes/.*")));
        SERVICES.verify(0, patchRequestedFor(urlPathMatching("/api/now/table/incident/.*")));
    }

    @Test
    void aResolvedCasesIncidentThatWasReopenedIsReadBackAsWithItsTeam() {
        String caseId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okCases("""
                [{"id": "%s", "type": "HANDOFF", "status": "RESOLVED", "incidentNumber": "INC0010034"}]"""
                .formatted(caseId))));
        stubIncident("INC0010034", "sys-34", "2", "Payments", "", caseId, Instant.now());

        poller.poll();

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010034", "status": "WITH_TEAM", "assignmentGroup": "Payments",
                         "incidentFinal": false,
                         "orderId": "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c"}""")));
    }

    @Test
    void readsBackAnIncidentThatNamesNoOrderAnyMoreWithoutAnOrder() {
        String caseId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okCases("""
                [{"id": "%s", "type": "SERVICE_DESK", "status": "WITH_TEAM", "incidentNumber": "INC0010035"}]"""
                .formatted(caseId))));
        String cleared = incidentRow("INC0010035", "sys-35", "2", "Payments", "", "", Instant.now())
                .replace(LINKED_ORDER, "");
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("sys_id=sys-35"))
                .willReturn(okJson("{\"result\": [" + cleared + "]}")));

        poller.poll();

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010035", "status": "WITH_TEAM", "assignmentGroup": "Payments",
                         "incidentFinal": false, "orderId": null}""")));
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
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okCases("""
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
                        {"number": "INC0010011", "status": "WITH_AGENT", "assignmentGroup": "Online Shop Agent",
                         "incidentFinal": false,
                         "orderId": "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c"}""")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + withTeam + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010012", "status": "WITH_TEAM", "assignmentGroup": "Payments",
                         "incidentFinal": false,
                         "orderId": "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c"}""")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + resolved + "/incident-state"))
                .withRequestBody(matchingJsonPath("$.status", equalTo("RESOLVED")))
                .withRequestBody(matchingJsonPath("$.incidentFinal", equalTo("true"))));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + takenByAPerson + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010014", "status": "WITH_TEAM",
                         "assignmentGroup": "Online Shop Agent (Incident Agent)", "incidentFinal": false,
                         "orderId": "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c"}""")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + onHold + "/incident-state"))
                .withRequestBody(equalToJson("""
                        {"number": "INC0010016", "status": "WITH_TEAM", "assignmentGroup": "Online Shop Agent (On Hold)",
                         "incidentFinal": false,
                         "orderId": "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c"}""")));
    }

    @Test
    void aNoteWhoseAnswerWasLostWhileAPersonResolvedTheIncidentIsSettledBeforeTheResolutionIsReported() {
        String caseId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(okCases("""
                [{"supportCase": {"id": "%s", "type": "HANDOFF", "status": "WITH_AGENT", "incidentNumber": "INC0010016"},
                  "unsentNotes": [{"id": "%s", "text": "The customer called again."}]}]"""
                .formatted(caseId, noteId))));
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okCases("""
                [{"id": "%s", "type": "HANDOFF", "status": "WITH_AGENT", "incidentNumber": "INC0010016"}]"""
                .formatted(caseId))));
        // ServiceNow applies the note, but its answer is lost; meanwhile a person resolves the incident.
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident")).inScenario("lost answer")
                .whenScenarioStateIs(Scenario.STARTED)
                .withQueryParam("sysparm_query", equalTo("sys_id=sys-16"))
                .withQueryParam("sysparm_display_value", equalTo("all"))
                .willReturn(okJson("{\"result\": [" + incidentRow("INC0010016", "sys-16", "2", "Online Shop Agent",
                        AGENT_USER, caseId, Instant.now()) + "]}")));
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident")).inScenario("lost answer")
                .whenScenarioStateIs(Scenario.STARTED)
                .withQueryParam("sysparm_query", equalTo("sys_id=sys-16"))
                .withQueryParam("sysparm_display_value", equalTo("true"))
                .willReturn(okJson("{\"result\": [{\"work_notes\": \"\", \"comments\": \"\"}]}")));
        SERVICES.stubFor(patch(urlPathEqualTo("/api/now/table/incident/sys-16")).inScenario("lost answer")
                .whenScenarioStateIs(Scenario.STARTED).willSetStateTo("resolved")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident")).inScenario("lost answer")
                .whenScenarioStateIs("resolved")
                .withQueryParam("sysparm_query", equalTo("sys_id=sys-16"))
                .withQueryParam("sysparm_display_value", equalTo("all"))
                .willReturn(okJson("{\"result\": [" + incidentRow("INC0010016", "sys-16", "6", "Online Shop Agent",
                        AGENT_USER, caseId, Instant.now()) + "]}")));
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident")).inScenario("lost answer")
                .whenScenarioStateIs("resolved")
                .withQueryParam("sysparm_query", equalTo("sys_id=sys-16"))
                .withQueryParam("sysparm_display_value", equalTo("true"))
                .willReturn(okJson("""
                        {"result": [{"work_notes": "2026-10-02 02:05:00 - Trailhead Agent (Work notes)\\nThe customer called again.\\n\\n[shop note %s]\\n\\n",
                                     "comments": ""}]}""".formatted(noteId))));
        String resolutionReport = "/api/agent/cases/" + caseId + "/incident-state";

        poller.poll();
        SERVICES.verify(0, postRequestedFor(urlEqualTo(resolutionReport)));
        poller.poll();

        SERVICES.verify(1, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-16")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + caseId + "/notes/" + noteId + "/sent")));
        SERVICES.verify(postRequestedFor(urlEqualTo(resolutionReport))
                .withRequestBody(matchingJsonPath("$.status", equalTo("RESOLVED"))));
        List<LoggedRequest> settledThenReported = SERVICES.findAll(postRequestedFor(urlPathMatching(
                "/api/agent/cases/" + caseId + "/(notes/.*/sent|incident-state)")));
        assertThat(settledThenReported).extracting(LoggedRequest::getUrl)
                .containsExactly("/api/agent/cases/" + caseId + "/notes/" + noteId + "/sent", resolutionReport);
    }

    @Test
    void whileTheCasesToSendCannotBeListedOwnersAreReadBackButNoResolution() {
        String takenCase = UUID.randomUUID().toString();
        String resolvedCase = UUID.randomUUID().toString();
        stubNewIncidents("[]");
        stubClaimed("[]");
        SERVICES.stubFor(get("/api/agent/cases/outgoing").willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(get("/api/agent/cases/in-servicenow").willReturn(okCases("""
                [{"id": "%s", "type": "HANDOFF", "status": "WITH_AGENT", "incidentNumber": "INC0010017"},
                 {"id": "%s", "type": "HANDOFF", "status": "WITH_AGENT", "incidentNumber": "INC0010018"}]"""
                .formatted(takenCase, resolvedCase))));
        stubIncident("INC0010017", "sys-17", "2", "Payments", "", takenCase, Instant.now());
        stubIncident("INC0010018", "sys-18", "6", "Online Shop Agent", "", resolvedCase, Instant.now());

        poller.poll();

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/" + takenCase + "/incident-state"))
                .withRequestBody(matchingJsonPath("$.status", equalTo("WITH_TEAM"))));
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/agent/cases/" + resolvedCase + "/incident-state")));
    }
}
