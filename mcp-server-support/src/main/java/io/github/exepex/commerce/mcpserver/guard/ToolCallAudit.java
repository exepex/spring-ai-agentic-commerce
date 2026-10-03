package io.github.exepex.commerce.mcpserver.guard;

/** Where this server records each tool call, under the calling agent's name. */
public interface ToolCallAudit {

    void notPermitted(String agentId, ToolCall call);

    void switchedOff(String agentId, ToolCall call);

    void succeeded(String agentId, ToolCall call);

    /** @param reason what stopped the call, as the model was told */
    void stopped(String agentId, ToolCall call, StoppedCallOutcome outcome, String reason);
}
