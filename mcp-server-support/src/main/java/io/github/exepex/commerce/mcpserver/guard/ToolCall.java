package io.github.exepex.commerce.mcpserver.guard;

import java.util.UUID;

/**
 * One call of a tool, as the audit trail records it.
 *
 * @param summary what the call does, in words a person reads on the timeline
 * @param orderId the order the call is about; null if none
 * @param auditsItself whether the tool records its own, more specific, entry when it succeeds
 */
public record ToolCall(String tool, String summary, UUID orderId, boolean auditsItself) {

    public static ToolCall of(String tool, String summary) {
        return new ToolCall(tool, summary, null, false);
    }
}
