package io.github.exepex.commerce.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Checkout and cancellation when something fails half way: a payment whose outcome is unknown, an order left behind
 * after its charge, a catalog that is down when stock must be given back, and the same order placed twice.
 */
class CheckoutRecoveryIntegrationTest extends OrderServiceTestSupport {

    @Autowired
    private OrderReconciler reconciler;

    @Autowired
    private CustomerOrderRepository orders;

    @Autowired
    private StockReleaseRepository stockReleases;

    @Test
    void aPaymentWithAnUnknownOutcomeIsAskedForAgainAndConfirmsTheOrder() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        MvcTestResult pending = placeOrder("ines@example.com", SHOE, 1);
        assertThat(pending).bodyJson().extractingPath("$.status").isEqualTo("PAYMENT_PENDING");
        String orderId = orderIdOf(pending);

        DEPENDENCIES.stubFor(post("/api/payments").willReturn(aResponse().withStatus(201)));
        reconciler.reconcile();

        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
        DEPENDENCIES.verify(2, paymentsFor(orderId));
        DEPENDENCIES.verify(0, deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        assertThat(orderEventTypesFor(orderId, 1)).containsExactly("ORDER_CONFIRMED");
    }

    @Test
    void aPendingPaymentThatIsThenDeclinedFailsTheOrderAndReleasesItsStock() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.SERVICE_UNAVAILABLE, "down")));
        String orderId = orderIdOf(placeOrder("jonas@example.com", SHOE, 1));

        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.PAYMENT_REQUIRED, "Your card was declined.")));
        reconciler.reconcile();

        assertThat(statusOf(orderId)).isEqualTo("PAYMENT_FAILED");
        DEPENDENCIES.verify(1, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
        assertThat(stockReleases.existsById(UUID.fromString(orderId))).isFalse();
    }

    @Test
    void anOrderLeftPlacedAfterItsChargeIsConfirmedByTheReconciler() throws Exception {
        UUID orderId = UUID.randomUUID();
        orders.save(CustomerOrder.place(orderId, "kim@example.com", "EUR",
                List.of(new OrderLine(SHOE, "RUN-SHOE-BLUE-42", "Trail running shoe, blue, EU 42", 1,
                        new BigDecimal("129.90"))),
                "pm_card_visa", Instant.now()));

        reconciler.reconcile();

        assertThat(statusOf(orderId.toString())).isEqualTo("CONFIRMED");
        assertThat(orderEventTypesFor(orderId.toString(), 1)).containsExactly("ORDER_CONFIRMED");
    }

    @Test
    void aCancellationWhileTheCatalogIsDownIsSavedAndItsStockReleasedLater() throws Exception {
        String orderId = orderIdOf(placeOrder("lena@example.com", SHOE, 1));
        DEPENDENCIES.stubFor(delete(urlMatching("/api/orders/.+/reservations")).willReturn(aResponse().withStatus(503)));

        MvcTestResult cancelled = cancel(orderId, "changed mind");

        assertThat(cancelled).hasStatusOk();
        assertThat(cancelled).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(stockReleases.existsById(UUID.fromString(orderId))).isTrue();

        DEPENDENCIES.stubFor(delete(urlMatching("/api/orders/.+/reservations")).willReturn(aResponse().withStatus(204)));
        reconciler.reconcile();

        DEPENDENCIES.verify(2, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
        assertThat(stockReleases.existsById(UUID.fromString(orderId))).isFalse();
    }

    @Test
    void stockReservedForARejectedOrderIsReleasedOnceTheCatalogIsBack() {
        DEPENDENCIES.stubFor(post("/api/products/" + HEADLAMP + "/reservations")
                .willReturn(problem(HttpStatus.CONFLICT, "Requested 1 of HEADLAMP-400 but only 0 available")));
        DEPENDENCIES.stubFor(delete(urlMatching("/api/orders/.+/reservations")).willReturn(aResponse().withStatus(503)));
        UUID orderId = UUID.randomUUID();

        assertThat(placeOrderWithId(orderId, "milan@example.com", SHOE, 1, HEADLAMP, 1)).hasStatus(HttpStatus.CONFLICT);
        assertThat(stockReleases.existsById(orderId)).isTrue();

        DEPENDENCIES.stubFor(delete(urlMatching("/api/orders/.+/reservations")).willReturn(aResponse().withStatus(204)));
        reconciler.reconcile();

        assertThat(stockReleases.existsById(orderId)).isFalse();
    }

    @Test
    void placingTheSameOrderAgainReturnsTheFirstOneWithoutChargingTwice() throws Exception {
        UUID orderId = UUID.randomUUID();

        MvcTestResult first = placeOrderWithId(orderId, "nora@example.com", SHOE, 1);
        MvcTestResult again = placeOrderWithId(orderId, "nora@example.com", SHOE, 1);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(again).hasStatus(HttpStatus.CREATED);
        assertThat(orderIdOf(again)).isEqualTo(orderId.toString());
        DEPENDENCIES.verify(1, paymentsFor(orderId.toString()));
        assertThat(placeOrderWithId(orderId, "someone-else@example.com", SHOE, 1)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void twoRequestsPlacingTheSameOrderAtOnceChargeItOnceAndKeepItsStock() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(aResponse().withStatus(201).withFixedDelay(1000)));
        UUID orderId = UUID.randomUUID();

        try (ExecutorService checkouts = Executors.newFixedThreadPool(2)) {
            Future<MvcTestResult> first = checkouts.submit(() -> placeOrderWithId(orderId, "olga@example.com", SHOE, 1));
            Future<MvcTestResult> second = checkouts.submit(() -> placeOrderWithId(orderId, "olga@example.com", SHOE, 1));

            assertThat(orderIdOf(first.get())).isEqualTo(orderId.toString());
            assertThat(orderIdOf(second.get())).isEqualTo(orderId.toString());
        }
        assertThat(statusOf(orderId.toString())).isEqualTo("CONFIRMED");
        DEPENDENCIES.verify(1, paymentsFor(orderId.toString()));
        DEPENDENCIES.verify(0, deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
    }

    /** Other tests' orders may still be pending, and the reconciler settles them too: count only this order's. */
    private static RequestPatternBuilder paymentsFor(String orderId) {
        return postRequestedFor(urlEqualTo("/api/payments")).withRequestBody(matchingJsonPath("$.orderId", equalTo(orderId)));
    }

    private String statusOf(String orderId) throws Exception {
        return JsonPath.read(mockMvc.get().uri("/api/orders/{orderId}", orderId).exchange()
                .getResponse().getContentAsString(), "$.status");
    }
}
