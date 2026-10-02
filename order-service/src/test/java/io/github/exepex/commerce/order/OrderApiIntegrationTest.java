package io.github.exepex.commerce.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Checkout and cancellation through the HTTP API. */
class OrderApiIntegrationTest extends OrderServiceTestSupport {

    @Test
    void placesAnOrderReservingEveryLineChargingTheTotalAndAnnouncingIt() throws Exception {
        MvcTestResult placed = placeOrder("ada@example.com", SHOE, 2, HEADLAMP, 1);

        assertThat(placed).hasStatus(HttpStatus.CREATED);
        assertThat(placed).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(placed).bodyJson().extractingPath("$.total").isEqualTo(299.30);
        String orderId = orderIdOf(placed);
        DEPENDENCIES.verify(postRequestedFor(urlEqualTo("/api/products/" + SHOE + "/reservations"))
                .withRequestBody(equalToJson("""
                        {"orderId": "%s", "quantity": 2}""".formatted(orderId))));
        DEPENDENCIES.verify(postRequestedFor(urlEqualTo("/api/payments")).withRequestBody(equalToJson("""
                {"orderId": "%s", "customerEmail": "ada@example.com", "amount": 299.30, "currency": "EUR",
                 "paymentMethod": "pm_card_visa"}""".formatted(orderId))));
        assertThat(mockMvc.get().uri("/api/orders/{orderId}", orderId))
                .bodyJson().extractingPath("$.lines.length()").isEqualTo(2);
        assertThat(orderEventTypesFor(orderId, 1)).containsExactly("ORDER_CONFIRMED");
    }

    @Test
    void rejectsTheOrderAndReleasesWhatWasReservedWhenStockRunsShort() {
        DEPENDENCIES.stubFor(post("/api/products/" + HEADLAMP + "/reservations")
                .willReturn(problem(HttpStatus.CONFLICT, "Requested 1 of HEADLAMP-400 but only 0 available")));

        MvcTestResult rejected = placeOrder("grace@example.com", SHOE, 1, HEADLAMP, 1);

        assertThat(rejected).hasStatus(HttpStatus.CONFLICT);
        assertThat(rejected).bodyJson().extractingPath("$.detail")
                .isEqualTo("Requested 1 of HEADLAMP-400 but only 0 available");
        DEPENDENCIES.verify(deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        assertThat(mockMvc.get().uri("/api/orders?customerEmail=grace@example.com"))
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void aDeclinedCardFailsTheOrderReleasesItsStockAndNamesTheOrder() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.PAYMENT_REQUIRED, "Your card was declined.")));

        MvcTestResult declined = placeOrder("alan@example.com", SHOE, 1);

        assertThat(declined).hasStatus(HttpStatus.PAYMENT_REQUIRED);
        assertThat(declined).bodyJson().extractingPath("$.detail").isEqualTo("Your card was declined.");
        DEPENDENCIES.verify(deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        MvcTestResult orders = mockMvc.get().uri("/api/orders?customerEmail=alan@example.com").exchange();
        assertThat(orders).bodyJson().extractingPath("$[0].status").isEqualTo("PAYMENT_FAILED");
        String failedOrderId = JsonPath.read(orders.getResponse().getContentAsString(), "$[0].id");
        assertThat(declined).bodyJson().extractingPath("$.orderId").isEqualTo(failedOrderId);
    }

    @Test
    void anUnavailablePaymentServiceLeavesThePaymentPendingAndKeepsTheStock() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.SERVICE_UNAVAILABLE, "down")));

        MvcTestResult pending = placeOrder("ken@example.com", SHOE, 1);

        assertThat(pending).hasStatus(HttpStatus.ACCEPTED);
        assertThat(pending).bodyJson().extractingPath("$.status").isEqualTo("PAYMENT_PENDING");
        DEPENDENCIES.verify(0, deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        DEPENDENCIES.verify(1, postRequestedFor(urlEqualTo("/api/payments")));
        assertThat(cancel(orderIdOf(pending), "changed mind")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void anOrderCannotBeCancelledWhileItsPaymentIsInFlight() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(aResponse().withStatus(201).withFixedDelay(1500)));

        try (ExecutorService checkout = Executors.newSingleThreadExecutor()) {
            Future<MvcTestResult> placing = checkout.submit(() -> placeOrder("leslie@example.com", SHOE, 1));
            String orderId = await().atMost(Duration.ofSeconds(5)).until(() -> placedOrderOf("leslie@example.com"),
                    id -> id != null);

            assertThat(cancel(orderId, "changed mind")).hasStatus(HttpStatus.CONFLICT);
            assertThat(placing.get()).hasStatus(HttpStatus.CREATED);
        }
        assertThat(mockMvc.get().uri("/api/orders?customerEmail=leslie@example.com"))
                .bodyJson().extractingPath("$[0].status").isEqualTo("CONFIRMED");
    }

    @Test
    void rejectsAnUnknownProduct() {
        assertThat(placeOrder("linus@example.com", UNKNOWN_PRODUCT, 1)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void rejectsTheSameProductOnTwoLinesWithoutCallingAnything() {
        assertThat(placeOrder("barbara@example.com", SHOE, 1, SHOE, 2)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(DEPENDENCIES.getAllServeEvents()).isEmpty();
    }

    @Test
    void reportsTheCatalogAsUnavailableWhenItCannotBeReached() {
        DEPENDENCIES.stubFor(get("/api/products/" + SHOE).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(placeOrder("edsger@example.com", SHOE, 1)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void cancellingReleasesTheStockOnceKeepsTheFirstReasonAndAnnouncesIt() throws Exception {
        String orderId = orderIdOf(placeOrder("margaret@example.com", SHOE, 1));

        MvcTestResult cancelled = cancel(orderId, "customer changed their mind");
        assertThat(cancelled).hasStatusOk();
        assertThat(cancelled).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");

        assertThat(cancel(orderId, "a second reason"))
                .bodyJson().extractingPath("$.cancellationReason").isEqualTo("customer changed their mind");
        DEPENDENCIES.verify(1, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
        assertThat(orderEventTypesFor(orderId, 2)).containsExactly("ORDER_CONFIRMED", "ORDER_CANCELLED");
    }

    @Test
    void anOrderWhosePaymentFailedCannotBeCancelled() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.PAYMENT_REQUIRED, "Your card was declined.")));
        placeOrder("dennis@example.com", SHOE, 1);
        String orderId = JsonPath.read(mockMvc.get().uri("/api/orders?customerEmail=dennis@example.com").exchange()
                .getResponse().getContentAsString(), "$[0].id");

        assertThat(cancel(orderId, "changed mind")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsAnInvalidEmail() {
        assertThat(placeOrder("not-an-email", SHOE, 1)).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
