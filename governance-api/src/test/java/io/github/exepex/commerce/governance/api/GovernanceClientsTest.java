package io.github.exepex.commerce.governance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.exepex.commerce.governance.api.client.AgentGovernanceClient;
import io.github.exepex.commerce.governance.api.client.AgentSwitchesClient;
import io.github.exepex.commerce.governance.api.dto.CaseStatus;
import io.github.exepex.commerce.governance.api.dto.IncidentState;
import io.github.exepex.commerce.governance.api.dto.SwitchChange;
import io.github.exepex.commerce.governance.api.dto.ToolCallOutcome;
import io.github.exepex.commerce.governance.api.dto.ToolCallReport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class GovernanceClientsTest {

    private static final String AGENT = "Bearer dev-incident-agent-token";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://governance");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(builder.build())).build();

    @Test
    void aToolCallIsReportedUnderTheAgentsOwnName() {
        server.expect(requestTo("http://governance/api/agent/tool-calls"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AGENT))
                .andExpect(content().json("""
                        {"orderId": null, "action": "servicenow:add_work_note", "outcome": "SUCCEEDED",
                         "summary": "Noted INC0010001", "details": null}"""))
                .andRespond(withSuccess());

        factory.createClient(AgentGovernanceClient.class).recordToolCall(AGENT,
                new ToolCallReport(null, "servicenow:add_work_note", ToolCallOutcome.SUCCEEDED, "Noted INC0010001", null));

        server.verify();
    }

    @Test
    void whoHasACaseIncidentIsReportedForThatCase() {
        var caseId = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001");
        server.expect(requestTo("http://governance/api/agent/cases/" + caseId + "/incident-state"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AGENT))
                .andExpect(content().json("""
                        {"number": "INC0010001", "status": "WITH_TEAM", "assignmentGroup": "Payments",
                         "incidentFinal": false, "orderId": null}"""))
                .andRespond(withSuccess());

        factory.createClient(AgentGovernanceClient.class).followIncident(AGENT, caseId,
                new IncidentState("INC0010001", CaseStatus.WITH_TEAM, "Payments", false, null));

        server.verify();
    }

    @Test
    void theKillSwitchesAreReadAndSet() {
        server.expect(requestTo("http://governance/api/agent-switches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"incident-agent\": false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://governance/api/agent-switches/incident-agent"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"enabled\": true, \"by\": \"operator\"}"))
                .andRespond(withSuccess("{\"incident-agent\": true}", MediaType.APPLICATION_JSON));

        var switches = factory.createClient(AgentSwitchesClient.class);

        assertThat(switches.all()).containsEntry("incident-agent", false);
        assertThat(switches.set("incident-agent", new SwitchChange(true, "operator")))
                .containsEntry("incident-agent", true);
        server.verify();
    }
}
