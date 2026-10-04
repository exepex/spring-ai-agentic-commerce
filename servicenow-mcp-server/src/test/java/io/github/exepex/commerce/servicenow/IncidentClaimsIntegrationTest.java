package io.github.exepex.commerce.servicenow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The poller claims new incidents in the agent's group and announces them, hands them to the default team while
 * the agent is switched off, and hands over claims the agent did not finish.
 */
class IncidentClaimsIntegrationTest extends ServiceNowMcpServerIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void whileAnotherInstancePollsThisOneLeavesServiceNowToIt() {
        stubNewIncidents("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "1"},
                  "assignment_group": {"display_value": "Online Shop Agent"}, "assigned_to": {"value": ""}}]""");
        stubClaimed("[]");
        stubIncident("", "1");
        stubServiceDeskIncidents(
                "[" + incidentRow("INC0010001", "sys-1", "1", "Online Shop Agent", "", "", Instant.now()) + "]");
        jdbc.update("""
                insert into servicenow.shedlock (name, lock_until, locked_at, locked_by)
                values ('incident-poll', now() + interval '1 hour', now(), 'another-instance')
                on conflict (name) do update set lock_until = excluded.lock_until, locked_by = excluded.locked_by""");
        try {
            poller.poll();

            assertThat(SERVICES.findAll(anyRequestedFor(urlPathMatching("/api/now/.*")))).isEmpty();
        } finally {
            jdbc.update("update servicenow.shedlock set lock_until = now() where name = 'incident-poll'");
        }

        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withRequestBody(matchingJsonPath("$.assigned_to", equalTo(AGENT_USER))));
    }

    @Test
    void claimsANewIncidentOnceAndAnnouncesIt() {
        stubNewIncidents("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"},
                  "short_description": {"value": "Order arrived broken"}, "state": {"value": "1", "display_value": "New"},
                  "assignment_group": {"display_value": "Online Shop Agent"}, "assigned_to": {"value": ""}}]""");
        stubClaimed("[]");
        stubIncident("", "1");
        // The service desk raised it about an order, so the shop records it first.
        stubServiceDeskIncidents(
                "[" + incidentRow("INC0010001", "sys-1", "1", "Online Shop Agent", "", "", Instant.now()) + "]");

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
    void whileTheAgentIsSwitchedOffANewIncidentGoesStraightToTheDefaultTeam() {
        SERVICES.stubFor(get("/api/agent-switches").willReturn(okJson("{\"incident-agent\": false}")));
        stubNewIncidents("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "1"},
                  "assignment_group": {"display_value": "Online Shop Agent"}, "assigned_to": {"value": ""}}]""");
        stubClaimed("[]");
        stubIncident("", "1");
        // The service desk raised it about an order, so the shop records it first.
        stubServiceDeskIncidents(
                "[" + incidentRow("INC0010001", "sys-1", "1", "Online Shop Agent", "", "", Instant.now()) + "]");

        poller.poll();

        SERVICES.verify(patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withQueryParam("sysparm_input_display_value", equalTo("true"))
                .withRequestBody(matchingJsonPath("$.assignment_group", equalTo("Customer Care")))
                .withRequestBody(matchingJsonPath("$.work_notes", containing("switched off"))));
        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1"))
                .withRequestBody(matchingJsonPath("$.assigned_to", equalTo(AGENT_USER))));
    }

    @Test
    void whileTheSwitchCannotBeReadNoNewIncidentIsClaimedOrHandedOver() {
        SERVICES.stubFor(get("/api/agent-switches").willReturn(aResponse().withStatus(503)));
        stubNewIncidents("""
                [{"sys_id": {"value": "sys-1"}, "number": {"value": "INC0010001"}, "state": {"value": "1"},
                  "assignment_group": {"display_value": "Online Shop Agent"}, "assigned_to": {"value": ""}}]""");
        stubClaimed("[]");
        stubIncident("", "1");

        poller.poll();

        SERVICES.verify(0, patchRequestedFor(urlPathEqualTo("/api/now/table/incident/sys-1")));
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
}
