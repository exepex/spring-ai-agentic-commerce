package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockOutSettlementTest {

    private static final UUID ORDER = UUID.randomUUID();

    private static ToolRun.ToolResult call(String tool, String result) {
        return callFor(ORDER, tool, result);
    }

    private static ToolRun.ToolResult callFor(UUID orderId, String tool, String result) {
        return new ToolRun.ToolResult(tool, "{\"orderId\": \"" + orderId + "\"}", result);
    }

    @Test
    void actionsOnAnotherOrderDoNotSettleThisOne() {
        UUID other = UUID.randomUUID();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                callFor(other, "get_order", "{\"status\": \"CANCELLED\", \"refunds\": [{\"status\": \"EXECUTED\"}]}"),
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                callFor(other, "issue_refund", "{\"status\": \"EXECUTED\"}"),
                callFor(other, "escalate_to_human", "{}")))).isFalse();
    }

    @Test
    void aCancelledOrderWithARefundPaidOrAwaitingApprovalIsSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                call("issue_refund", "{\"status\": \"EXECUTED\"}")))).isTrue();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                call("issue_refund", "{\"status\": \"PENDING_APPROVAL\"}")))).isTrue();
    }

    @Test
    void anOrderHandedToAPersonIsSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(call("escalate_to_human", "{\"id\": \"e-1\"}")))).isTrue();
    }

    @Test
    void anOrderFoundAlreadyCancelledAndRefundedIsSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(call("get_order",
                "{\"status\": \"CANCELLED\", \"refunds\": [{\"status\": \"EXECUTED\"}]}")))).isTrue();
    }

    @Test
    void aRunThatOnlyLookedOrWhoseRefundFailedIsNotSettled() {
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(call("get_order", "{\"status\": \"CONFIRMED\", \"refunds\": []}"))))
                .isFalse();
        assertThat(StockOutSettlement.isSettled(ORDER, List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                call("issue_refund", "{\"status\": \"FAILED\"}")))).isFalse();
    }
}
