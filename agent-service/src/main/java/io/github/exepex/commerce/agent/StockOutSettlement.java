package io.github.exepex.commerce.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decides, from the tool calls that succeeded in a run, whether the agent dealt with a stock-out order. It did when it
 * handed the order to a person, or when the order is cancelled, its money is settled, and the customer was notified.
 * The money is settled when the stock-out's own refund (key {@code refund-<order id>-stockout}) is paid or waiting for
 * approval or, while that refund has not failed, when nothing is left to refund beyond the refunds already waiting for
 * approval. Each can have happened in this run or, for a replayed stock-out, an earlier one that {@code get_order} shows. Only calls made for
 * that order count, and the model's own summary is not trusted for this.
 */
final class StockOutSettlement {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> SETTLED_REFUNDS = Set.of("EXECUTED", "PENDING_APPROVAL");

    private StockOutSettlement() {}

    static String refundKeyFor(UUID orderId) {
        return "refund-" + orderId + "-stockout";
    }

    static boolean isSettled(UUID orderId, List<ToolRun.ToolResult> allCalls) {
        List<ToolRun.ToolResult> calls = allCalls.stream()
                .filter(call -> orderId.toString().equals(json(call.arguments()).path("orderId").asString("")))
                .toList();
        List<JsonNode> lookups = calls.stream().filter(call -> "get_order".equals(call.tool())).map(StockOutSettlement::json)
                .toList();
        String refundKey = refundKeyFor(orderId);
        boolean escalated = calls.stream().anyMatch(call -> "escalate_to_human".equals(call.tool()));
        boolean cancelled = calls.stream().anyMatch(call -> "cancel_order".equals(call.tool())
                        && "CANCELLED".equals(json(call).path("status").asString("")))
                || lookups.stream().anyMatch(order -> "CANCELLED".equals(order.path("status").asString("")));
        boolean refunded = calls.stream().anyMatch(call -> "issue_refund".equals(call.tool())
                        && refundKey.equals(json(call.arguments()).path("idempotencyKey").asString(""))
                        && SETTLED_REFUNDS.contains(json(call).path("status").asString("")))
                || lookups.stream().flatMap(order -> order.path("refunds").valueStream())
                        .anyMatch(refund -> refundKey.equals(refund.path("idempotencyKey").asString(""))
                                && SETTLED_REFUNDS.contains(refund.path("status").asString("")))
                || lookups.stream().anyMatch(order -> !stockOutRefundFailed(order, refundKey) && nothingLeftToRefund(order));
        boolean notified = calls.stream().anyMatch(call -> "notify_customer".equals(call.tool()))
                || lookups.stream().anyMatch(order -> !order.path("notifications").isEmpty());
        return escalated || (cancelled && refunded && notified);
    }

    /**
     * A failed request may still have reached the card processor, so a payment with nothing left to refund does not
     * show it was paid: only retrying its key settles it.
     */
    private static boolean stockOutRefundFailed(JsonNode order, String refundKey) {
        return order.path("refunds").valueStream().anyMatch(refund -> refundKey.equals(refund.path("idempotencyKey").asString(""))
                && "FAILED".equals(refund.path("status").asString("")));
    }

    /**
     * The payment was taken and all of it is returned or waiting for approval, for example after the customer
     * cancelled earlier.
     */
    private static boolean nothingLeftToRefund(JsonNode order) {
        JsonNode payment = order.path("payment");
        if (!"SUCCEEDED".equals(payment.path("status").asString("")) || !payment.path("refundable").isNumber()) {
            return false;
        }
        BigDecimal waitingForApproval = order.path("refunds").valueStream()
                .filter(refund -> "PENDING_APPROVAL".equals(refund.path("status").asString("")))
                .filter(refund -> refund.path("amount").isNumber())
                .map(refund -> refund.path("amount").decimalValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return payment.path("refundable").decimalValue().subtract(waitingForApproval).signum() <= 0;
    }

    private static JsonNode json(ToolRun.ToolResult call) {
        return json(call.result());
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text == null ? "" : text);
        } catch (RuntimeException notJson) {
            return JSON.missingNode();
        }
    }
}
