package io.github.exepex.commerce.evals;

import static io.github.exepex.commerce.evals.Demo.INCIDENT_AGENT;
import static io.github.exepex.commerce.evals.Demo.count;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

/**
 * The incident workflows end to end, with a real model: what happens to a parcel after it ships, and an incident the
 * service desk raises. They play the carrier through the shop's API, and the service desk and the teams through
 * ServiceNow's Table API, so they run against the simulator or a real instance alike (see {@link ServiceNow}).
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class IncidentScenarioEvals {

    private static final Duration AGENT_TIMEOUT = Duration.ofMinutes(6);

    private final Demo demo = new Demo();
    private final ServiceNow serviceNow = new ServiceNow();

    @Test
    void aFailedDeliveryGoesToFulfilmentWhoCloseItInServiceNow() {
        String orderId = demo.placeOrder(Demo.newCustomer(), Demo.HEADLAMP);
        demo.ship(orderId);
        demo.reportFromCarrier(orderId, "DELIVERY_FAILED", "Nobody home, parcel returned to the depot");

        JsonNode supportCase = demo.awaitCaseFinished(orderId);

        assertThat(supportCase.path("type").asString()).isEqualTo("DELIVERY_FAILED");
        assertThat(supportCase.path("status").asString()).isEqualTo("WITH_TEAM");
        assertThat(supportCase.path("assignmentGroup").asString()).isEqualTo("Fulfilment");
        assertThat(demo.refundRequests(orderId)).isEmpty();
        serviceNow.resolveAsTeam(supportCase.path("incidentNumber").asString(), "Delivered again on Monday.");
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(demo.casesOf(orderId).getFirst().path("status").asString()).isEqualTo("RESOLVED"));
    }

    @Test
    void aLostParcelIsRefundedOnceExplainedAndResolved() {
        String orderId = demo.placeOrder(Demo.newCustomer(), Demo.HEADLAMP);
        demo.ship(orderId);
        demo.reportFromCarrier(orderId, "LOST", "The carrier lost the parcel in transit");

        JsonNode supportCase = demo.awaitCaseFinished(orderId);

        assertThat(supportCase.path("type").asString()).isEqualTo("PARCEL_LOST");
        assertThat(supportCase.path("status").asString()).isEqualTo("RESOLVED");
        assertThat(count(demo.timeline(orderId), INCIDENT_AGENT, "issue_refund", "SUCCEEDED")).isEqualTo(1);
        JsonNode payment = demo.payment(orderId);
        assertThat(payment.path("refundedAmount").decimalValue()).isEqualByComparingTo(payment.path("amount").decimalValue());
        assertThat(demo.notifications(orderId)).hasSize(1);
    }

    @Test
    void anIncidentTheServiceDeskRaisesAboutADeliveredOrderEndsResolvedOrWithATeam() {
        String orderId = demo.placeOrder(Demo.newCustomer(), Demo.HEADLAMP);
        demo.ship(orderId);
        demo.reportFromCarrier(orderId, "DELIVERED", "");

        String number = serviceNow.raiseIncident("Order arrived broken, the customer wants their money back",
                "The customer sent photos of the cracked lens and asks for a refund.", orderId);

        await().atMost(AGENT_TIMEOUT).pollInterval(Duration.ofSeconds(5)).until(() ->
                Set.of("Resolved", "Closed").contains(serviceNow.stateOf(number))
                        || !serviceNow.agentGroup().equals(serviceNow.assignmentGroupOf(number)));
        List<JsonNode> refunds = demo.refundRequests(orderId);
        assertThat(refunds).hasSizeLessThanOrEqualTo(1);
        if ("Resolved".equals(serviceNow.stateOf(number))) {
            assertThat(refunds).singleElement().satisfies(refund ->
                    assertThat(refund.path("status").asString()).isIn("EXECUTED", "PENDING_APPROVAL"));
        }
        assertThat(demo.order(orderId).path("status").asString()).isEqualTo("DELIVERED");
    }
}
