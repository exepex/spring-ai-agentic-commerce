package io.github.exepex.commerce.agents;

import io.github.exepex.commerce.agents.constants.PromptPlaceholders;
import java.util.List;

/**
 * Everything that defines one agent, read from its file in {@code agents/}: the model and how hard it thinks, the
 * tools it may use on each MCP server, how many tool calls one run may make, whether it acts for a single customer,
 * and its instructions. Secrets such as the agent's bearer token are not part of it; they come from the environment.
 *
 * @param id the agent's identity, as used in tokens, the audit trail and the kill switch
 * @param model the Claude model the agent runs on
 * @param effort how hard Claude thinks: low, medium, high, xhigh or max
 * @param toolCallBudget the most tool calls one run may make
 * @param customerScoped whether the agent acts for one customer and may only touch that customer's orders
 * @param commerceTools the commerce MCP tools the agent may call
 * @param slackTools the Slack MCP tools the agent may call, if Slack is configured
 * @param servicenowTools the ServiceNow MCP tools the agent may call, if ServiceNow is configured
 * @param instructions the system prompt
 * @param slackInstructions the extra step added to the prompt when Slack is configured, or empty
 */
public record AgentDefinition(String id, String model, String effort, int toolCallBudget, boolean customerScoped,
        List<String> commerceTools, List<String> slackTools, List<String> servicenowTools, String instructions,
        String slackInstructions) {

    /** The system prompt, with the Slack step filled in for the given channel, or left out without one. */
    public String systemPrompt(String slackChannelId) {
        var slackStep = slackChannelId == null || slackChannelId.isBlank() || slackInstructions.isBlank()
                ? ""
                : slackInstructions.replace(PromptPlaceholders.SLACK_CHANNEL, slackChannelId);
        return instructions.replace(PromptPlaceholders.SLACK_STEP, slackStep).strip();
    }
}
