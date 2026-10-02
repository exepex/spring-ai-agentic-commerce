package io.github.exepex.commerce.agent;

import java.util.function.BooleanSupplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Wraps an MCP tool before the model sees it. Two governance rules live here, in code rather than in the prompt:
 *
 * <ul>
 *   <li><b>Identity is not the model's choice.</b> For a customer-facing agent the {@code customerEmail} parameter is
 *       removed from the schema the model sees and filled in from the signed-in customer on every call.</li>
 *   <li><b>Every run has a tool-call budget.</b> Once it is spent, further calls are refused, so a confused agent
 *       cannot loop.</li>
 *   <li><b>A switched-off agent stops.</b> A third-party MCP server, such as Slack's, cannot enforce the kill switch,
 *       so its tools check the switch before every call. The commerce MCP server enforces it itself.</li>
 * </ul>
 */
final class AgentToolCallback implements ToolCallback {

    static final String CUSTOMER_EMAIL = "customerEmail";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ToolCallback mcpTool;
    private final boolean injectsCustomer;
    private final BooleanSupplier agentSwitchedOn;
    private final ToolDefinition definition;

    AgentToolCallback(ToolCallback mcpTool, boolean injectsCustomer, BooleanSupplier agentSwitchedOn) {
        this.mcpTool = mcpTool;
        this.injectsCustomer = injectsCustomer;
        this.agentSwitchedOn = agentSwitchedOn;
        this.definition = injectsCustomer ? withoutCustomerParameter(mcpTool.getToolDefinition()) : mcpTool.getToolDefinition();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        throw new IllegalStateException("Agent tools are only called within an agent run");
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        ToolRun run = (ToolRun) toolContext.getContext().get(ToolRun.CONTEXT_KEY);
        if (!run.takeCall()) {
            return "Refused: this run has used its tool-call budget. Stop calling tools; summarise what you did and, "
                    + "if work is left, say that a human must finish it.";
        }
        if (!agentSwitchedOn.getAsBoolean()) {
            return "Refused: this agent has been switched off. Stop calling tools; a human will take over.";
        }
        String input = toolInput;
        if (injectsCustomer) {
            ObjectNode arguments = (ObjectNode) JSON.readTree(toolInput == null || toolInput.isBlank() ? "{}" : toolInput);
            arguments.put(CUSTOMER_EMAIL, run.customerEmail());
            input = JSON.writeValueAsString(arguments);
        }
        // A call the MCP server refused throws here, so only successful calls are recorded.
        String result = mcpTool.call(input);
        run.recordSuccess(definition.name(), input, textOf(result));
        return result;
    }

    /** An MCP tool result reaches us as its JSON content list; what the tool returned is the text of its content. */
    private static String textOf(String mcpContent) {
        StringBuilder text = new StringBuilder();
        for (JsonNode content : JSON.readTree(mcpContent)) {
            text.append(content.path("text").asString(""));
        }
        return text.toString();
    }

    private static ToolDefinition withoutCustomerParameter(ToolDefinition original) {
        ObjectNode schema = (ObjectNode) JSON.readTree(original.inputSchema());
        if (schema.get("properties") instanceof ObjectNode properties) {
            properties.remove(CUSTOMER_EMAIL);
        }
        if (schema.get("required") instanceof ArrayNode required) {
            for (int index = required.size() - 1; index >= 0; index--) {
                if (CUSTOMER_EMAIL.equals(required.get(index).asString())) {
                    required.remove(index);
                }
            }
        }
        return ToolDefinition.builder()
                .name(original.name())
                .description(original.description())
                .inputSchema(JSON.writeValueAsString(schema))
                .build();
    }
}
