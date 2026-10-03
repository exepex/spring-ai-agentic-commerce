package io.github.exepex.commerce.mcpserver.guard;

/**
 * How this server words a refusal for the model, and which failures of a tool are rules denying it rather than
 * something going wrong.
 */
public interface ToolRefusals {

    RuntimeException notPermitted(String agentId, String tool);

    RuntimeException switchedOff(String agentId);

    ToolCallOutcome outcomeOf(RuntimeException stopped);
}
