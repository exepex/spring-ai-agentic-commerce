package io.github.exepex.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Runs against Postgres with the simulated card processor, which follows Stripe's test-card conventions. Refunds are
 * only checked with the processor when a test asks for it.
 */
@SpringBootTest(properties = {"commerce.payments.stripe-secret-key=", "commerce.payments.refund-check.interval=1h"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentApiIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private RefundReconciler refundReconciler;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void endAnyOutage() {
        setOutage(false);
    }

    @Test
    void chargesAnOrderOnlyOnce() throws Exception {
        UUID orderId = UUID.randomUUID();

        MvcTestResult first = charge(orderId, "129.90", "pm_card_visa");
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(first).bodyJson().extractingPath("$.status").isEqualTo("SUCCEEDED");

        assertThat(charge(orderId, "129.90", "pm_card_visa"))
                .bodyJson().extractingPath("$.id").isEqualTo(idOf(first));
    }

    @Test
    void reportsADeclinedCard() {
        MvcTestResult declined = charge(UUID.randomUUID(), "24.00", "pm_card_chargeDeclined");

        assertThat(declined).hasStatus(HttpStatus.PAYMENT_REQUIRED);
        assertThat(declined).bodyJson().extractingPath("$.detail").isEqualTo("Your card was declined.");
    }

    @Test
    void aRepeatedRefundReturnsTheFirstOneAndNeverRefundsTwice() throws Exception {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "100.00", "pm_card_visa");

        MvcTestResult first = refund(orderId, "40.00", "refund-key-" + orderId);
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(refund(orderId, "40.00", "refund-key-" + orderId))
                .bodyJson().extractingPath("$.id").isEqualTo(idOf(first));

        assertThat(mockMvc.get().uri("/api/payments/{orderId}", orderId))
                .bodyJson().extractingPath("$.refundable").isEqualTo(60.0);
    }

    @Test
    void aRefundThatLaterFailsAtTheProcessorNoLongerCountsAsRefunded() throws Exception {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "100.00", "pm_card_refundFail");
        MvcTestResult refunded = refund(orderId, "40.00", "refund-fails-" + orderId);
        assertThat(refunded).bodyJson().extractingPath("$.status").isEqualTo("SUCCEEDED");

        refundReconciler.reconcile();
        refundReconciler.reconcile();

        MvcTestResult payment = mockMvc.get().uri("/api/payments/{orderId}", orderId).exchange();
        assertThat(payment).bodyJson().extractingPath("$.refundable").isEqualTo(100.0);
        assertThat(payment).bodyJson().extractingPath("$.refunds[0].status").isEqualTo("FAILED");
        assertThat(refund(orderId, "40.00", "refund-fails-" + orderId)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void aRefundPendingForLongerThanTheWatchIsStillWatchedOnceItSucceeds() {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "100.00", "pm_card_refundFail");
        refund(orderId, "40.00", "refund-pending-long-" + orderId);
        // As if the processor had kept the refund pending for two hours.
        jdbc.update("""
                update payments.refund set status = 'PENDING', succeeded_at = null, created_at = now() - interval '2 hours'
                where idempotency_key = ?""", "refund-pending-long-" + orderId);

        refundReconciler.reconcile();
        refundReconciler.reconcile();

        MvcTestResult payment = mockMvc.get().uri("/api/payments/{orderId}", orderId).exchange();
        assertThat(payment).bodyJson().extractingPath("$.refunds[0].status").isEqualTo("FAILED");
        assertThat(payment).bodyJson().extractingPath("$.refundable").isEqualTo(100.0);
    }

    @Test
    void onlyRefundsOfPaymentsTheCurrentProcessorTookAreChecked() {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "100.00", "pm_card_refundFail");
        refund(orderId, "40.00", "refund-other-processor-" + orderId);
        // As if the payment had been taken through Stripe before the demo was switched to the simulator.
        jdbc.update("update payments.payment set provider = 'stripe' where order_id = ?", orderId);

        refundReconciler.reconcile();

        MvcTestResult payment = mockMvc.get().uri("/api/payments/{orderId}", orderId).exchange();
        assertThat(payment).bodyJson().extractingPath("$.refundable").isEqualTo(60.0);
        assertThat(payment).bodyJson().extractingPath("$.refunds[0].status").isEqualTo("SUCCEEDED");
    }

    @Test
    void aRefundThatSucceededStaysRefundedWhenCheckedAgain() {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "100.00", "pm_card_visa");
        refund(orderId, "40.00", "refund-holds-" + orderId);

        refundReconciler.reconcile();

        MvcTestResult payment = mockMvc.get().uri("/api/payments/{orderId}", orderId).exchange();
        assertThat(payment).bodyJson().extractingPath("$.refundable").isEqualTo(60.0);
        assertThat(payment).bodyJson().extractingPath("$.refunds[0].status").isEqualTo("SUCCEEDED");
    }

    @Test
    void aRepeatedChargeWithADifferentAmountIsRefusedInsteadOfReportedAsPaid() {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "10.00", "pm_card_visa");

        assertThat(charge(orderId, "129.90", "pm_card_visa")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsAnIdempotencyKeyLongerThanItCanStoreBeforeRefundingAnything() {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "50.00", "pm_card_visa");

        assertThat(refund(orderId, "10.00", "k".repeat(201))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mockMvc.get().uri("/api/payments/{orderId}", orderId))
                .bodyJson().extractingPath("$.refundable").isEqualTo(50.0);
    }

    @Test
    void rejectsARefundReasonLongerThanItCanStoreBeforeRefundingAnything() {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "50.00", "pm_card_visa");

        assertThat(mockMvc.post().uri("/api/payments/{orderId}/refunds", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": 10.00, "reason": "%s", "idempotencyKey": "long-reason-%s"}"""
                        .formatted("x".repeat(501), orderId)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mockMvc.get().uri("/api/payments/{orderId}", orderId))
                .bodyJson().extractingPath("$.refundable").isEqualTo(50.0);
    }

    @Test
    void rejectsAReusedKeyForADifferentAmountAndARefundAboveWhatIsLeft() {
        UUID orderId = UUID.randomUUID();
        charge(orderId, "50.00", "pm_card_visa");
        refund(orderId, "30.00", "key-a-" + orderId);

        assertThat(refund(orderId, "10.00", "key-a-" + orderId)).hasStatus(HttpStatus.CONFLICT);
        assertThat(refund(orderId, "30.00", "key-b-" + orderId)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void theSimulatedOutageTakesThePaymentApiDownButNotTheSwitch() {
        setOutage(true);

        assertThat(charge(UUID.randomUUID(), "10.00", "pm_card_visa")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(mockMvc.get().uri("/api/admin/simulated-outage"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.active").isEqualTo(true);
    }

    private MvcTestResult charge(UUID orderId, String amount, String paymentMethod) {
        return mockMvc.post().uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId": "%s", "customerEmail": "ada@example.com", "amount": %s, "currency": "EUR",
                         "paymentMethod": "%s"}""".formatted(orderId, amount, paymentMethod))
                .exchange();
    }

    private MvcTestResult refund(UUID orderId, String amount, String idempotencyKey) {
        return mockMvc.post().uri("/api/payments/{orderId}/refunds", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s, "reason": "item out of stock", "idempotencyKey": "%s"}"""
                        .formatted(amount, idempotencyKey))
                .exchange();
    }

    private void setOutage(boolean active) {
        mockMvc.put().uri("/api/admin/simulated-outage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"active": %s}""".formatted(active))
                .exchange();
    }

    private static String idOf(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
