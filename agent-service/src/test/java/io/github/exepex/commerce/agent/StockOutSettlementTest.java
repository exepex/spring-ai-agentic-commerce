package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockOutSettlementTest {

    private static final UUID ORDER = UUID.randomUUID();
    private static final String STOCK_OUT_KEY = StockOutSettlement.refundKeyFor(ORDER);

    private static ToolRun.ToolResult call(String tool, String result) {
        return callFor(ORDER, tool, result);
    }

    private static ToolRun.ToolResult callFor(UUID orderId, String tool, String result) {
        return new ToolRun.ToolResult(tool, "{\"orderId\": \"" + orderId + "\"}", result);
    }

    private static ToolRun.ToolResult refund(String key, String status) {
        return new ToolRun.ToolResult("issue_refund", "{\"orderId\": \"" + ORDER + "\", \"idempotencyKey\": \"" + key + "\"}",
                "{\"status\": \"" + status + "\"}");
    }

    /** What get_order returns for the order, with its refunds and how often the customer was notified. */
    private static ToolRun.ToolResult lookup(String status, String refunds, int notifications) {
        String sent = notifications == 0 ? "" : "{\"sentAt\": \"2026-10-02T10:00:00Z\", \"sentBy\": \"order-exceptions-agent\"}";
        return call("get_order", "{\"status\": \"" + status + "\", \"refunds\": [" + refunds + "], \"notifications\": ["
                + sent + "]}");
    }

    private static String refundOf(String key, String status) {
        return "{\"idempotencyKey\": \"" + key + "\", \"status\": \"" + status + "\"}";
    }

    @Test
    void aCancelledRefundedAndNotifiedOrderIsSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                refund(STOCK_OUT_KEY, "EXECUTED"),
                call("notify_customer", "{}")))).isTrue();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                refund(STOCK_OUT_KEY, "PENDING_APPROVAL"),
                call("notify_customer", "{}")))).isTrue();
    }

    @Test
    void anOrderHandedToAPersonIsSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(call("escalate_to_human", "{\"id\": \"e-1\"}")))).isTrue();
    }

    @Test
    void aReplayThatFindsEveryStepDoneIsSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                lookup("CANCELLED", refundOf(STOCK_OUT_KEY, "PENDING_APPROVAL"), 1)))).isTrue();
    }

    @Test
    void anEarlierPartialRefundDoesNotCountAsTheStockOutRefund() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                lookup("CANCELLED", refundOf("refund-" + ORDER + "-1", "EXECUTED"), 1)))).isFalse();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                refund("refund-" + ORDER + "-other", "EXECUTED"),
                call("notify_customer", "{}")))).isFalse();
    }

    @Test
    void aCustomerWhoWasNeverToldLeavesTheOrderUnsettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                lookup("CANCELLED", refundOf(STOCK_OUT_KEY, "EXECUTED"), 0)))).isFalse();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                lookup("CANCELLED", refundOf(STOCK_OUT_KEY, "EXECUTED"), 0),
                call("notify_customer", "{}")))).isTrue();
    }

    @Test
    void actionsOnAnotherOrderDoNotSettleThisOne() {
        UUID other = UUID.randomUUID();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                callFor(other, "issue_refund", "{\"status\": \"EXECUTED\"}"),
                callFor(other, "notify_customer", "{}"),
                callFor(other, "escalate_to_human", "{}")))).isFalse();
    }

    @Test
    void aRunThatOnlyLookedOrWhoseRefundFailedIsNotSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(lookup("CONFIRMED", "", 0)))).isFalse();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                refund(STOCK_OUT_KEY, "FAILED"),
                call("notify_customer", "{}")))).isFalse();
    }
}
