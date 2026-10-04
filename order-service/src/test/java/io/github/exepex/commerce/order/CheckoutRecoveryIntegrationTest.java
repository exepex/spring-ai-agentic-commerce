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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbc;

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
    void whileAnotherInstanceReconcilesThisOneLeavesTheOrdersToIt() throws Exception {
        // The first run creates the job's lock; then another instance holds it.
        reconciler.reconcile();
        jdbc.update("""
                update orders.shedlock set lock_until = now() + interval '1 hour', locked_by = 'another-instance'
                where name = 'order-reconciliation'""");
        UUID stalled = UUID.randomUUID();
        orders.save(CustomerOrder.place(stalled, "lena@example.com", "EUR",
                List.of(new OrderLine(SHOE, "RUN-SHOE-BLUE-42", "Trail running shoe, blue, EU 42", 1,
                        new BigDecimal("129.90"))),
                "pm_card_visa", Instant.now()));
        try {
            reconciler.reconcile();

            assertThat(statusOf(stalled.toString())).isEqualTo("PLACED");
            DEPENDENCIES.verify(0, paymentsFor(stalled.toString()));
        } finally {
            jdbc.update("update orders.shedlock set lock_until = now() where name = 'order-reconciliation'");
        }

        reconciler.reconcile();

        assertThat(statusOf(stalled.toString())).isEqualTo("CONFIRMED");
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
    void cancellingTheSameOrderTwiceAtOnceSucceedsBothTimesAndReleasesTheStockOnce() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            String orderId = orderIdOf(placeOrder("pia@example.com", SHOE, 1));
            List<Integer> statuses = new CopyOnWriteArrayList<>();
            try (ExecutorService customers = Executors.newFixedThreadPool(2)) {
                CountDownLatch start = new CountDownLatch(1);
                for (int customer = 0; customer < 2; customer++) {
                    customers.submit(() -> {
                        start.await();
                        statuses.add(cancel(orderId, "changed mind").getResponse().getStatus());
                        return null;
                    });
                }
                start.countDown();
            }
            assertThat(statuses).containsExactly(200, 200);
            assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
            DEPENDENCIES.verify(1, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
        }
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
    void aBacklogOfStockReleasesIsWorkedOffOldestFirstInBoundedRuns() {
        DEPENDENCIES.stubFor(delete(urlMatching("/api/orders/.+/reservations")).willReturn(aResponse().withStatus(204)));
        var backlog = new ArrayList<UUID>();
        var longAgo = Instant.parse("2000-01-01T00:00:00Z");
        for (int i = 0; i < 60; i++) {
            backlog.add(UUID.randomUUID());
            jdbc.update("insert into orders.stock_release (order_id, requested_at) values (?, ?)",
                    backlog.getLast(), Timestamp.from(longAgo.plusSeconds(i)));
        }

        reconciler.reconcile();

        assertThat(backlog.subList(0, 50)).noneMatch(stockReleases::existsById);
        assertThat(backlog.subList(50, 60)).allMatch(stockReleases::existsById);

        reconciler.reconcile();

        assertThat(backlog).noneMatch(stockReleases::existsById);
    }

    @Test
    void theOldestStockReleasesAreFoundThroughAnIndex() {
        // An empty table is cheapest to scan; the planner shows which index it can use once scanning is ruled out.
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("set enable_seqscan = off");
                try (var rows = statement.executeQuery(
                        "explain select * from orders.stock_release order by requested_at limit 50")) {
                    var lines = new StringBuilder();
                    while (rows.next()) {
                        lines.append(rows.getString(1)).append('\n');
                    }
                    return lines.toString();
                } finally {
                    statement.execute("reset enable_seqscan");
                }
            }
        });

        assertThat(plan).contains("stock_release_requested");
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
