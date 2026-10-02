package io.github.exepex.commerce.agent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decides, from the tool calls that succeeded in a run, whether the agent dealt with a stock-out order: it handed the
 * order to a person, or the order is cancelled and its refund paid or waiting for approval. Only calls made for that
 * order count, and the model's own summary is not trusted for this.
 */
final class StockOutSettlement {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> SETTLED_REFUNDS = Set.of("EXECUTED", "PENDING_APPROVAL");

    private StockOutSettlement() {}

    static boolean isSettled(UUID orderId, List<ToolRun.ToolResult> allCalls) {
        List<ToolRun.ToolResult> calls = allCalls.stream()
                .filter(call -> orderId.toString().equals(json(call.arguments()).path("orderId").asString("")))
                .toList();
        boolean escalated = calls.stream().anyMatch(call -> "escalate_to_human".equals(call.tool()));
        boolean cancelled = calls.stream().anyMatch(call -> ("cancel_order".equals(call.tool())
                || "get_order".equals(call.tool())) && "CANCELLED".equals(json(call).path("status").asString("")));
        boolean refunded = calls.stream().anyMatch(call -> "issue_refund".equals(call.tool())
                        && SETTLED_REFUNDS.contains(json(call).path("status").asString("")))
                || calls.stream().filter(call -> "get_order".equals(call.tool()))
                        .flatMap(call -> json(call).path("refunds").valueStream())
                        .anyMatch(refund -> SETTLED_REFUNDS.contains(refund.path("status").asString("")));
        return escalated || (cancelled && refunded);
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
