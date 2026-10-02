package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StockOutSettlementTest {

    private static ToolRun.ToolResult call(String tool, String result) {
        return new ToolRun.ToolResult(tool, result);
    }

    @Test
    void aCancelledOrderWithARefundPaidOrAwaitingApprovalIsSettled() {
        assertThat(StockOutSettlement.isSettled(List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                call("issue_refund", "{\"status\": \"EXECUTED\"}")))).isTrue();
        assertThat(StockOutSettlement.isSettled(List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                call("issue_refund", "{\"status\": \"PENDING_APPROVAL\"}")))).isTrue();
    }

    @Test
    void anOrderHandedToAPersonIsSettled() {
        assertThat(StockOutSettlement.isSettled(List.of(call("escalate_to_human", "{\"id\": \"e-1\"}")))).isTrue();
    }

    @Test
    void anOrderFoundAlreadyCancelledAndRefundedIsSettled() {
        assertThat(StockOutSettlement.isSettled(List.of(call("get_order",
                "{\"status\": \"CANCELLED\", \"refunds\": [{\"status\": \"EXECUTED\"}]}")))).isTrue();
    }

    @Test
    void aRunThatOnlyLookedOrWhoseRefundFailedIsNotSettled() {
        assertThat(StockOutSettlement.isSettled(List.of(call("get_order", "{\"status\": \"CONFIRMED\", \"refunds\": []}"))))
                .isFalse();
        assertThat(StockOutSettlement.isSettled(List.of(
                call("cancel_order", "{\"status\": \"CANCELLED\"}"),
                call("issue_refund", "{\"status\": \"FAILED\"}")))).isFalse();
    }
}
