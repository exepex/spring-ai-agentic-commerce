package io.github.exepex.commerce.agents;

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
 * @param instructions the system prompt
 * @param slackInstructions the extra step added to the prompt when Slack is configured, or empty
 */
public record AgentDefinition(String id, String model, String effort, int toolCallBudget, boolean customerScoped,
        List<String> commerceTools, List<String> slackTools, String instructions, String slackInstructions) {

    /** Where the Slack step goes in the instructions; {@value #SLACK_CHANNEL} inside it becomes the channel id. */
    public static final String SLACK_STEP = "{slackStep}";
    public static final String SLACK_CHANNEL = "{slackChannelId}";

    /** The system prompt, with the Slack step filled in for the given channel, or left out without one. */
    public String systemPrompt(String slackChannelId) {
        String slackStep = slackChannelId == null || slackChannelId.isBlank() || slackInstructions.isBlank()
                ? ""
                : slackInstructions.replace(SLACK_CHANNEL, slackChannelId);
        return instructions.replace(SLACK_STEP, slackStep).strip();
    }
}
