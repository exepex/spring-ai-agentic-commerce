package io.github.exepex.commerce.servicenow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Incidents the service desk raised about an order are recorded with the shop as cases, and claimed only once the
 * shop has them under the order they name.
 */
class ServiceDeskIncidentsIntegrationTest extends ServiceNowMcpServerIntegrationTestSupport {

    @Test
    void aNewIncidentTheServiceDeskRaisedAboutAnOrderIsRecordedWithTheShopAsTheAgentsAndClaimed() {
        String row = incidentRow("INC0010020", "sys-1", "1", "Online Shop Agent", "", "", Instant.now());
        stubServiceDeskIncidents("[" + row + "]");
        stubNewIncidents("[" + row + "]");
        stubClaimed("[]");
        stubIncident("INC0010020", "sys-1", "1", "Online Shop Agent", "", "", Instant.now());

        poller.poll();

        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/service-desk"))
                .withHeader("Authorization", equalTo("Bearer " + AGENT_TOKEN))
                .withRequestBody(equalToJson("""
                        {"orderId": "%s", "number": "INC0010020", "url": "%s/incident.do?sys_id=sys-1",
                         "shortDescription": "Order arrived broken", "status": "WITH_AGENT",
                         "assignmentGroup": "Online Shop Agent"}""".formatted(LINKED_ORDER, SERVICES.baseUrl()))));
        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withRequestBody(matchingJsonPath("$.assigned_to", equalTo(AGENT_USER))));
    }

    @Test
    void anIncidentTheServiceDeskGaveATeamIsRecordedAsTheTeamsAndOneNamingNoOrderOrACaseIsLeftOut() {
        String withPayments = incidentRow("INC0010021", "sys-21", "2", "Payments", "", "Raised by the CRM", Instant.now());
        String noOrder = incidentRow("INC0010022", "sys-22", "2", "Payments", "", "", Instant.now())
                .replace(LINKED_ORDER, "the blue one");
        String shopCase = incidentRow("INC0010024", "sys-24", "2", "Payments", "", UUID.randomUUID().toString(),
                Instant.now());
        stubServiceDeskIncidents("[" + withPayments + ", " + noOrder + ", " + shopCase + "]");
        stubNewIncidents("[]");
        stubClaimed("[]");

        poller.poll();

        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/agent/cases/service-desk")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/service-desk"))
                .withRequestBody(matchingJsonPath("$.number", equalTo("INC0010021")))
                .withRequestBody(matchingJsonPath("$.status", equalTo("WITH_TEAM")))
                .withRequestBody(matchingJsonPath("$.assignmentGroup", equalTo("Payments"))));
    }

    @Test
    void everyOpenServiceDeskIncidentIsRecordedHoweverManyComeBeforeIt() {
        List<String> namingNoOrder = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            namingNoOrder.add(incidentRow("INC00200%02d".formatted(i), "sys-x" + i, "2", "Payments", "", "", Instant.now())
                    .replace(LINKED_ORDER, "not an order"));
        }
        stubServiceDeskIncidents("0", "[" + String.join(", ", namingNoOrder) + "]");
        stubServiceDeskIncidents("100",
                "[" + incidentRow("INC0010023", "sys-23", "2", "Payments", "", "", Instant.now()) + "]");
        stubNewIncidents("[]");
        stubClaimed("[]");

        poller.poll();

        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/agent/cases/service-desk")));
        SERVICES.verify(postRequestedFor(urlEqualTo("/api/agent/cases/service-desk"))
                .withRequestBody(matchingJsonPath("$.number", equalTo("INC0010023"))));
    }

    @Test
    void aServiceDeskIncidentTheShopCouldNotBeToldAboutIsNotClaimedUntilItIs() {
        String row = incidentRow("INC0010025", "sys-1", "1", "Online Shop Agent", "", "", Instant.now());
        stubServiceDeskIncidents("[" + row + "]");
        stubNewIncidents("[" + row + "]");
        stubClaimed("[]");
        stubIncident("INC0010025", "sys-1", "1", "Online Shop Agent", "", "", Instant.now());
        SERVICES.stubFor(post("/api/agent/cases/service-desk").willReturn(aResponse().withStatus(503)));

        poller.poll();
        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
        SERVICES.stubFor(post("/api/agent/cases/service-desk").willReturn(aResponse().withStatus(200)));
        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withRequestBody(matchingJsonPath("$.assigned_to", equalTo(AGENT_USER))));
    }

    @Test
    void aServiceDeskIncidentMovedToAnotherOrderSinceItWasRecordedIsNotClaimedUntilItsCaseMoves() {
        String recordedRow = incidentRow("INC0010027", "sys-1", "1", "Online Shop Agent", "", "", Instant.now());
        stubServiceDeskIncidents("[" + recordedRow + "]");
        stubNewIncidents("[" + recordedRow + "]");
        stubClaimed("[]");
        // Re-read right before the claim, the service desk has corrected the order meanwhile.
        String otherOrder = UUID.randomUUID().toString();
        SERVICES.stubFor(get(urlPathEqualTo("/api/now/table/incident"))
                .withQueryParam("sysparm_query", equalTo("number=INC0010027"))
                .willReturn(okJson("{\"result\": [" + recordedRow.replace(LINKED_ORDER, otherOrder) + "]}")));

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
    }

    @Test
    void whileTheAgentIsSwitchedOffAServiceDeskIncidentTheShopCouldNotBeToldAboutStillGoesToTheDefaultTeam() {
        SERVICES.stubFor(get("/api/agent-switches").willReturn(okJson("{\"incident-agent\": false}")));
        String row = incidentRow("INC0010026", "sys-1", "1", "Online Shop Agent", "", "", Instant.now());
        stubServiceDeskIncidents("[" + row + "]");
        stubNewIncidents("[" + row + "]");
        stubClaimed("[]");
        stubIncident("INC0010026", "sys-1", "1", "Online Shop Agent", "", "", Instant.now());
        SERVICES.stubFor(post("/api/agent/cases/service-desk").willReturn(aResponse().withStatus(503)));

        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withRequestBody(matchingJsonPath("$.assignment_group", equalTo("Customer Care"))));
    }
}
