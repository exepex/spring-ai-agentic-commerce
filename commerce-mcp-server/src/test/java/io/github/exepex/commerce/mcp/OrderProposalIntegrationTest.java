package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.jayway.jsonpath.JsonPath;
import io.github.exepex.commerce.mcp.governance.ProposalReconciler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * An agent proposes an order and only the customer's confirmation places it. The proposal's id is the order's id, so
 * a confirmation whose outcome is not known yet can place the same order again without placing a second one.
 */
class OrderProposalIntegrationTest extends McpServerTestSupport {

    private final UUID productId = UUID.randomUUID();

    @Autowired
    private ProposalReconciler reconciler;

    @BeforeEach
    void stubTheCatalog() {
        SERVICES.stubFor(get("/api/products").willReturn(okJson("""
                [{"id": "%s", "sku": "HEADLAMP-400", "name": "Headlamp", "description": "", "price": 39.50,
                  "currency": "EUR", "onHand": 5, "reserved": 0, "available": 5}]""".formatted(productId))));
    }

    @Test
    void confirmingTheSameProposalTwiceAtOncePlacesOneOrderUnderTheProposalsId() throws Exception {
        String proposalId = propose();
        stubPlacedOrder(proposalId, "CONFIRMED", 201);

        runTogether(() -> confirm(proposalId), () -> confirm(proposalId));

        SERVICES.verify(1, placementsOf(proposalId));
        assertThat(status(proposalId)).isEqualTo("CONFIRMED");
    }

    @Test
    void aDeclinedConfirmationShowsOnTheTimelineOfTheOrderItFailed() {
        String proposalId = propose();
        SERVICES.stubFor(post("/api/orders").willReturn(aResponse().withStatus(402)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                        {"status": 402, "detail": "Your card was declined.", "orderId": "%s"}""".formatted(proposalId))));

        String confirmed = rest().post().uri("/api/order-proposals/{id}/confirm", proposalId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("paymentMethod", "pm_card_chargeDeclined"))
                .retrieve().body(String.class);

        assertThat((String) JsonPath.read(confirmed, "$.status")).isEqualTo("FAILED");
        assertThat(timeline(proposalId)).contains("confirm_order", "FAILED", "Your card was declined.");
    }

    @Test
    void aConfirmationWhosePaymentIsPendingIsSettledOnceThePaymentIs() {
        String proposalId = propose();
        stubPlacedOrder(proposalId, "PAYMENT_PENDING", 202);

        confirm(proposalId);
        assertThat(status(proposalId)).isEqualTo("CONFIRMING");
        assertThat((String) JsonPath.read(proposal(proposalId), "$.orderId")).isEqualTo(proposalId);
        assertThat(timeline(proposalId)).doesNotContain("confirm_order");

        stubPlacedOrder(proposalId, "CONFIRMED", 201);
        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(status(proposalId)).isEqualTo("CONFIRMED");
        List<String> confirmations = JsonPath.read(timeline(proposalId), "$..[?(@.action == 'confirm_order')].outcome");
        assertThat(confirmations).containsExactly("SUCCEEDED");
    }

    @Test
    void aConfirmationTheOrderServiceDidNotAnswerPlacesTheSameOrderAgain() {
        String proposalId = propose();
        SERVICES.stubFor(post("/api/orders").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        confirm(proposalId);
        assertThat(status(proposalId)).isEqualTo("CONFIRMING");

        stubPlacedOrder(proposalId, "CONFIRMED", 201);
        reconciler.reconcile();

        assertThat(status(proposalId)).isEqualTo("CONFIRMED");
        SERVICES.verify(2, placementsOf(proposalId).withRequestBody(matchingJsonPath("$.paymentMethod", equalTo("pm_card_visa"))));
    }

    @Test
    void aConfirmationWhoseOrderWasCancelledMeanwhileEndsAsConfirmed() {
        String proposalId = propose();
        SERVICES.stubFor(post("/api/orders").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        confirm(proposalId);

        stubPlacedOrder(proposalId, "CANCELLED", 201);
        reconciler.reconcile();

        assertThat(status(proposalId)).isEqualTo("CONFIRMED");
        assertThat((String) JsonPath.read(proposal(proposalId), "$.orderId")).isEqualTo(proposalId);
    }

    @Test
    void aConfirmationWhoseOrderShippedMeanwhileEndsAsConfirmed() {
        String proposalId = propose();
        SERVICES.stubFor(post("/api/orders").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        confirm(proposalId);

        stubPlacedOrder(proposalId, "SHIPPED", 201);
        reconciler.reconcile();

        assertThat(status(proposalId)).isEqualTo("CONFIRMED");
    }

    private String propose() {
        return JsonPath.read(text(call(assistant, "propose_order", Map.of("customerEmail", "ada@example.com",
                "lines", List.of(Map.of("productId", productId.toString(), "quantity", 1))))), "$.id");
    }

    private static void stubPlacedOrder(String orderId, String status, int httpStatus) {
        SERVICES.stubFor(post("/api/orders").willReturn(aResponse().withStatus(httpStatus)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": "%s", "customerEmail": "ada@example.com", "status": "%s", "total": 39.50, "currency": "EUR",
                         "createdAt": "2026-10-02T10:00:00Z", "lines": []}""".formatted(orderId, status))
                .withFixedDelay(300)));
    }

    private static RequestPatternBuilder placementsOf(String orderId) {
        return postRequestedFor(urlEqualTo("/api/orders")).withRequestBody(matchingJsonPath("$.orderId", equalTo(orderId)));
    }

    private int confirm(String proposalId) {
        return rest().post().uri("/api/order-proposals/{id}/confirm", proposalId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("paymentMethod", "pm_card_visa"))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private String proposal(String proposalId) {
        return rest().get().uri("/api/order-proposals/{id}", proposalId).retrieve().body(String.class);
    }

    private String status(String proposalId) {
        return JsonPath.read(proposal(proposalId), "$.status");
    }

    private String timeline(String orderId) {
        return rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve().body(String.class);
    }
}
