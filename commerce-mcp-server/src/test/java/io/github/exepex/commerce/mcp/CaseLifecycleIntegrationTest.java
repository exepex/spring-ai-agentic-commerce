package io.github.exepex.commerce.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Every problem that needs handling becomes a case: one per order and type, carried to ServiceNow by its poller, which
 * reports back who has the incident.
 */
class CaseLifecycleIntegrationTest extends CaseTestSupport {

    @Test
    void theSameProblemRaisedAgainOrTwiceAtOnceKeepsOneCaseAndAddsNotes() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "39.50");

        runTogether(() -> handOff(orderId, "first"), () -> handOff(orderId, "second"));
        handOff(orderId, "third");

        List<String> openCases = JsonPath.read(cases(orderId), "$[*].status");
        assertThat(openCases).containsExactly("PENDING");
        String outgoing = outgoingFor(orderId);
        assertThat((List<String>) JsonPath.read(outgoing, "$[*].unsentNotes[*].text")).hasSize(2);
        assertThat((String) JsonPath.read(outgoing, "$[0].supportCase.title")).startsWith("[HANDOFF] Order ");
        List<String> summaries = JsonPath.read(timeline(orderId), "$[?(@.action == 'raise_case')].summary");
        assertThat(summaries).hasSize(3);
        assertThat(summaries.getFirst()).startsWith("Opened a HANDOFF case");
        assertThat(summaries.subList(1, 3)).allMatch(summary -> summary.startsWith("Added to the open HANDOFF case"));
    }

    @Test
    void aStockOutDeliveredAgainOpensOneCasePerOrderItNames() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String stockOut = """
                {"eventId": "%s", "occurredAt": "2026-10-02T09:15:00Z", "productId": "%s", "sku": "RUN-SHOE-BLUE-43",
                 "onHand": 0, "reserved": 2, "shortfall": 2, "reason": "water damage", "affectedOrderIds": ["%s", "%s"]}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), first, second);

        kafka.send("inventory.stock-out", "product", stockOut).join();
        kafka.send("inventory.stock-out", "product", stockOut).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat((List<String>) JsonPath.read(cases(first), "$[*].type")).containsExactly("STOCK_OUT");
            assertThat((List<String>) JsonPath.read(cases(second), "$[*].type")).containsExactly("STOCK_OUT");
        });
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat((List<?>) JsonPath.read(outgoingFor(first), "$[0].unsentNotes")).isEmpty();
            assertThat((String) JsonPath.read(cases(first), "$[0].description")).contains("water damage");
        });
    }

    @Test
    void aFailedDeliveryAndALostParcelEachOpenACase() {
        UUID failed = UUID.randomUUID();
        UUID lost = UUID.randomUUID();

        kafka.send("shipment.events", failed.toString(), shipmentEvent(failed, "SHIPMENT_DELIVERY_FAILED",
                "Nobody home, parcel returned")).join();
        kafka.send("shipment.events", lost.toString(), shipmentEvent(lost, "SHIPMENT_LOST", "The carrier lost the parcel"))
                .join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat((List<String>) JsonPath.read(cases(failed), "$[*].type")).containsExactly("DELIVERY_FAILED");
            assertThat((List<String>) JsonPath.read(cases(lost), "$[*].type")).containsExactly("PARCEL_LOST");
        });
        assertThat((String) JsonPath.read(cases(failed), "$[0].description")).contains("Nobody home, parcel returned");
    }

    @Test
    void thePollerCarriesTheCaseToServiceNowAndFollowsItsIncident() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        handOff(orderId, "second");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        String noteId = JsonPath.read(outgoingFor(orderId), "$[0].unsentNotes[0].id");

        assertThat(sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010001",
                "url", "https://example.service-now.com/incident.do?sysparm_query=number=INC0010001"))).isEqualTo(200);
        assertThat(sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010001"))).isEqualTo(200);
        assertThat(sync("/api/agent/cases/{id}/notes/" + noteId + "/sent", caseId, Map.of())).isEqualTo(200);

        assertThat(outgoingFor(orderId)).isEqualTo("[]");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].status")).isEqualTo("WITH_AGENT");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].incidentNumber")).isEqualTo("INC0010001");

        assertThat(followIncident(caseId, "INC0010001", "WITH_TEAM", "Payments")).isEqualTo(200);
        assertThat(followIncident(caseId, "INC0010001", "WITH_TEAM", "Payments")).isEqualTo(200);
        assertThat(followIncident(caseId, "INC0099999", "RESOLVED", "Payments")).isEqualTo(409);
        assertThat(followIncident(caseId, "INC0010001", "RESOLVED", "Payments")).isEqualTo(200);

        List<String> steps = JsonPath.read(timeline(orderId),
                "$[?(@.action == 'open_incident' || @.action == 'follow_incident')].summary");
        assertThat(steps).containsExactly("Opened ServiceNow incident INC0010001 for the HANDOFF case",
                "INC0010001 is assigned to Payments", "INC0010001 is resolved");
        handOff(orderId, "again");
        List<String> statuses = JsonPath.read(cases(orderId), "$[*].status");
        assertThat(statuses).containsExactly("RESOLVED", "PENDING");
    }

    @Test
    void aResolvedCasesIncidentReopenedWithATeamOpensTheCaseAgain() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010031"));
        followIncident(caseId, "INC0010031", "RESOLVED", "Online Shop Agent");

        boolean readBackWhileResolved = inServiceNow().contains(caseId);
        assertThat(followIncident(caseId, "INC0010031", "WITH_TEAM", "Payments")).isEqualTo(200);

        assertThat(readBackWhileResolved).isTrue();
        assertThat((String) JsonPath.read(cases(orderId), "$[0].status")).isEqualTo("WITH_TEAM");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].assignmentGroup")).isEqualTo("Payments");
        assertThat((List<String>) JsonPath.read(timeline(orderId), "$[?(@.action == 'follow_incident')].summary"))
                .contains("INC0010031 was reopened and is assigned to Payments");
    }

    @Test
    void onceTheNewerCaseIsResolvedTheReopenedOneTakesTheProblemRaisedAgain() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        String earlier = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", earlier, Map.of("number", "INC0010036"));
        followIncident(earlier, "INC0010036", "RESOLVED", "Online Shop Agent");
        handOff(orderId, "the customer called again");
        String newer = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", newer, Map.of("number", "INC0010037"));
        followIncident(earlier, "INC0010036", "WITH_TEAM", "Payments");
        followIncident(newer, "INC0010037", "RESOLVED", "Online Shop Agent");

        handOff(orderId, "and once more");

        assertThat((List<String>) JsonPath.read(cases(orderId), "$[?(@.status != 'RESOLVED')].id"))
                .containsExactly(earlier);
        assertThat((List<String>) JsonPath.read(outgoingFor(orderId), "$[?(@.supportCase.id == '" + earlier
                + "')].unsentNotes[*].text")).anySatisfy(text -> assertThat(text).contains("once more"));
    }

    @Test
    void anIncidentReopenedIntoTheGroupItWasResolvedInOpensItsCaseAgain() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010035"));
        followIncident(caseId, "INC0010035", "RESOLVED", "Payments");

        assertThat(followIncident(caseId, "INC0010035", "WITH_TEAM", "Payments")).isEqualTo(200);

        assertThat((String) JsonPath.read(cases(orderId), "$[0].status")).isEqualTo("WITH_TEAM");
    }

    @Test
    void aCaseWhoseIncidentIsClosedIsNoLongerReadBack() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010032"));

        assertThat(followIncident(caseId, "INC0010032", "RESOLVED", "Online Shop Agent", true)).isEqualTo(200);

        assertThat(inServiceNow()).doesNotContain(caseId);
    }

    @Test
    void whatWasRaisedAgainButNeverReachedAResolvedIncidentGoesToANewCase() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010003"));
        handOff(orderId, "the customer called again");

        assertThat(followIncident(caseId, "INC0010003", "RESOLVED", "Online Shop Agent")).isEqualTo(200);

        List<String> statuses = JsonPath.read(cases(orderId), "$[*].status");
        assertThat(statuses).containsExactly("RESOLVED", "PENDING");
        String outgoing = outgoingFor(orderId);
        assertThat((List<String>) JsonPath.read(outgoing, "$[*].supportCase.id")).doesNotContain(caseId);
        assertThat((String) JsonPath.read(outgoing, "$[0].supportCase.description"))
                .startsWith("Raised again after INC0010003 was resolved");
        assertThat((List<String>) JsonPath.read(outgoing, "$[0].unsentNotes[*].text"))
                .containsExactly("the customer called again");
    }

    @Test
    void onlyTheCaseWorkerMaySyncCases() {
        int asAssistant = RestClient.create("http://localhost:" + port).get().uri("/api/agent/cases/outgoing")
                .header("Authorization", "Bearer " + ASSISTANT_TOKEN)
                .exchange((request, response) -> response.getStatusCode().value());
        int anonymous = rest().get().uri("/api/agent/cases/outgoing")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(asAssistant).isEqualTo(403);
        assertThat(anonymous).isEqualTo(401);
    }
}
