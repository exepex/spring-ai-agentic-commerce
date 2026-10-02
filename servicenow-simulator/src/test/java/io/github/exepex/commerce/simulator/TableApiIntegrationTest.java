package io.github.exepex.commerce.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * The simulator answers the Table API calls servicenow-mcp-server makes, the way a real instance does: the same
 * queries, value and display-value pairs, display-value input, and work notes kept as journal entries.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"simulator.username=agent.user", "simulator.password=secret"})
class TableApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void letsInOnlyTheIntegrationUser() {
        int anonymous = RestClient.create("http://localhost:" + port).get().uri("/api/now/table/incident")
                .exchange((request, response) -> response.getStatusCode().value());
        int wrongPassword = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("agent.user", "guess")).build()
                .get().uri("/api/now/table/incident").exchange((request, response) -> response.getStatusCode().value());

        assertThat(anonymous).isEqualTo(401);
        assertThat(wrongPassword).isEqualTo(401);
    }

    @Test
    void anIncidentOpenedInTheAgentsGroupIsFoundAsNewWorkedAndHandedToATeam() {
        String agentUser = JsonPath.read(get("/api/now/table/sys_user?sysparm_query=user_name=agent.user"
                + "&sysparm_fields=sys_id&sysparm_limit=1"), "$.result[0].sys_id");
        String created = api().post().uri("/api/now/table/incident?sysparm_input_display_value=true"
                        + "&sysparm_fields=sys_id,number,assignment_group&sysparm_display_value=all")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("assignment_group", "Online Shop Agent", "short_description", "[HANDOFF] help",
                        "correlation_display", "case-1"))
                .retrieve().body(String.class);
        String sysId = JsonPath.read(created, "$.result.sys_id.value");
        String number = JsonPath.read(created, "$.result.number.value");

        String fresh = get("/api/now/table/incident?sysparm_query=assignment_group.name=Online Shop Agent"
                + "^assigned_toISEMPTY^state=1&sysparm_display_value=all");
        patch(sysId, false, Map.of("assigned_to", agentUser, "state", "2", "work_notes", "Picked up."));
        patch(sysId, true, Map.of("assignment_group", "Payments", "assigned_to", "", "work_notes", "Refund it."));

        assertThat(number).matches("INC\\d{7}");
        assertThat(JsonPath.<List<String>>read(fresh, "$.result[*].number.value")).contains(number);
        String handed = get("/api/now/table/incident?sysparm_query=correlation_display=case-1&sysparm_display_value=all");
        assertThat(JsonPath.<String>read(handed, "$.result[0].assignment_group.display_value")).isEqualTo("Payments");
        assertThat(JsonPath.<String>read(handed, "$.result[0].state.display_value")).isEqualTo("In Progress");
        assertThat(JsonPath.<String>read(handed, "$.result[0].assigned_to.value")).isEmpty();
        String journal = get("/api/now/table/sys_journal_field?sysparm_query=element_id=" + sysId
                + "^ORDERBYDESCsys_created_on&sysparm_fields=element,value,sys_created_by");
        assertThat(JsonPath.<List<String>>read(journal, "$.result[*].value")).containsExactly("Refund it.", "Picked up.");
        assertThat(JsonPath.<List<String>>read(journal, "$.result[*].sys_created_by")).containsOnly("agent.user");
    }

    @Test
    void refusesAGroupThatDoesNotExist() {
        int status = api().post().uri("/api/now/table/incident?sysparm_input_display_value=true")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("assignment_group", "Legal"))
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(400);
    }

    private String get(String uri) {
        return api().get().uri(uri).retrieve().body(String.class);
    }

    private void patch(String sysId, boolean displayValues, Map<String, String> fields) {
        api().patch().uri("/api/now/table/incident/{sysId}?sysparm_input_display_value={display}", sysId, displayValues)
                .contentType(MediaType.APPLICATION_JSON).body(fields).retrieve().toBodilessEntity();
    }

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("agent.user", "secret"))
                .build();
    }
}
