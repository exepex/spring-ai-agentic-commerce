package io.github.exepex.commerce.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * The simulator answers the Table API calls servicenow-mcp-server makes, the way a real instance does: the same
 * queries, value and display-value pairs, display-value input, and work notes shown on the incident, not as journal rows.
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
        String claimedQuery = "/api/now/table/incident?sysparm_query=assignment_group.name=Online Shop Agent"
                + "^assigned_to=" + agentUser + "^state=2&sysparm_display_value=all";
        String claimed = get(claimedQuery);
        patch(sysId, true, Map.of("assignment_group", "Payments", "assigned_to", "", "work_notes", "Refund it."));

        assertThat(number).matches("INC\\d{7}");
        assertThat(JsonPath.<List<String>>read(fresh, "$.result[*].number.value")).contains(number);
        assertThat(JsonPath.<List<String>>read(claimed, "$.result[*].number.value")).contains(number);
        assertThat(JsonPath.<List<String>>read(get(claimedQuery), "$.result[*].number.value")).doesNotContain(number);
        String handed = get("/api/now/table/incident?sysparm_query=correlation_display=case-1&sysparm_display_value=all");
        assertThat(JsonPath.<String>read(handed, "$.result[0].assignment_group.display_value")).isEqualTo("Payments");
        assertThat(JsonPath.<String>read(handed, "$.result[0].state.display_value")).isEqualTo("In Progress");
        assertThat(JsonPath.<String>read(handed, "$.result[0].assigned_to.value")).isEmpty();
        String shown = get("/api/now/table/incident?sysparm_query=correlation_display=case-1&sysparm_display_value=true");
        assertThat(JsonPath.<String>read(shown, "$.result[0].assignment_group.display_value")).isEqualTo("Payments");
        assertThat(JsonPath.<String>read(shown, "$.result[0].assignment_group.link")).contains("/sys_user_group/");
        assertThat(JsonPath.<String>read(shown, "$.result[0].state")).isEqualTo("In Progress");
        String notes = get("/api/now/table/incident?sysparm_query=sys_id=" + sysId
                + "&sysparm_fields=work_notes,comments,sys_updated_on&sysparm_display_value=all");
        assertThat(JsonPath.<String>read(notes, "$.result[0].work_notes.value")).isEmpty();
        assertThat(JsonPath.<String>read(notes, "$.result[0].work_notes.display_value")).matches(
                "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} - Agent User \\(Work notes\\)\nRefund it\\.\n\n"
                + "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} - Agent User \\(Work notes\\)\nPicked up\\.\n\n");
        assertThat(JsonPath.<String>read(notes, "$.result[0].comments.display_value")).isEmpty();
        String journal = get("/api/now/table/sys_journal_field?sysparm_query=element_id=" + sysId);
        assertThat(JsonPath.<List<Object>>read(journal, "$.result")).as("journal rows, hidden as from an itil user").isEmpty();
    }

    @Test
    void findsTheOpenIncidentsAboutAnOrderThatNoCaseOpened() {
        String order = UUID.randomUUID().toString();
        String raised = create(Map.of("assignment_group", "Payments", "short_description", "Customer called",
                "correlation_id", order));
        String ofACase = create(Map.of("assignment_group", "Online Shop Agent", "short_description", "[HANDOFF] help",
                "correlation_id", order, "correlation_display", UUID.randomUUID().toString()));
        String resolvedSysId = create(Map.of("assignment_group", "Payments", "short_description", "Refunded already",
                "correlation_id", order));
        patch(resolvedSysId, false, Map.of("state", "6"));

        String found = get("/api/now/table/incident?sysparm_query=correlation_id=" + order
                + "^correlation_idISNOTEMPTY^correlation_displayISEMPTY^stateNOT IN6,7,8&sysparm_display_value=all");

        assertThat(JsonPath.<List<String>>read(found, "$.result[*].sys_id.value"))
                .containsExactly(raised).doesNotContain(ofACase, resolvedSysId);
    }

    @Test
    void findsSeveralIncidentsAtOnceByTheirSysIds() {
        String first = create(Map.of("assignment_group", "Payments", "short_description", "First"));
        String second = create(Map.of("assignment_group", "Payments", "short_description", "Second"));
        String other = create(Map.of("assignment_group", "Payments", "short_description", "Not asked for"));

        String found = get("/api/now/table/incident?sysparm_query=sys_idIN" + first + "," + second
                + "&sysparm_display_value=all");

        assertThat(JsonPath.<List<String>>read(found, "$.result[*].sys_id.value"))
                .containsExactlyInAnyOrder(first, second).doesNotContain(other);
    }

    @Test
    void refusesAGroupThatDoesNotExist() {
        int status = api().post().uri("/api/now/table/incident?sysparm_input_display_value=true")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("assignment_group", "Legal"))
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(400);
    }

    /** Opens an incident and returns its sys_id. */
    private String create(Map<String, String> fields) {
        String created = api().post().uri("/api/now/table/incident?sysparm_input_display_value=true&sysparm_fields=sys_id")
                .contentType(MediaType.APPLICATION_JSON).body(fields).retrieve().body(String.class);
        return JsonPath.read(created, "$.result.sys_id");
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
