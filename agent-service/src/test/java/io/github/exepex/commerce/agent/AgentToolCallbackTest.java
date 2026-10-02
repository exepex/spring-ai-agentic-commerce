package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class AgentToolCallbackTest {

    private static final String SCHEMA = """
            {"type": "object",
             "properties": {"orderId": {"type": "string"}, "customerEmail": {"type": "string"}},
             "required": ["orderId", "customerEmail"]}""";

    /** Stands in for an MCP tool and remembers the arguments it was called with. */
    private static final class RecordingTool implements ToolCallback {

        private final String name;
        private final List<String> inputs = new ArrayList<>();

        RecordingTool(String name) {
            this.name = name;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(name).description("a tool").inputSchema(SCHEMA).build();
        }

        @Override
        public String call(String toolInput) {
            inputs.add(toolInput);
            return "{\"id\": \"proposal-1\"}";
        }
    }

    @Test
    void hidesTheCustomerFromTheModelAndFillsInTheSignedInCustomer() {
        RecordingTool mcpTool = new RecordingTool("get_order");
        AgentToolCallback tool = new AgentToolCallback(mcpTool, true);

        assertThat(tool.getToolDefinition().inputSchema()).doesNotContain("customerEmail").contains("orderId");

        tool.call("{\"orderId\": \"o-1\", \"customerEmail\": \"someone-else@example.com\"}",
                contextFor(new ToolRun("ada@example.com", 5)));

        assertThat(mcpTool.inputs.getFirst()).contains("\"customerEmail\":\"ada@example.com\"").doesNotContain("someone-else");
    }

    @Test
    void leavesTheSchemaAloneForAnAgentThatIsNotCustomerFacing() {
        AgentToolCallback tool = new AgentToolCallback(new RecordingTool("get_order"), false);

        assertThat(tool.getToolDefinition().inputSchema()).contains("customerEmail");
    }

    @Test
    void refusesCallsOnceTheRunHasSpentItsBudget() {
        RecordingTool mcpTool = new RecordingTool("get_order");
        AgentToolCallback tool = new AgentToolCallback(mcpTool, false);
        ToolContext context = contextFor(new ToolRun(null, 2));

        tool.call("{}", context);
        tool.call("{}", context);
        String third = tool.call("{}", context);

        assertThat(third).startsWith("Refused");
        assertThat(mcpTool.inputs).hasSize(2);
    }

    @Test
    void collectsOrderProposalsForTheChatToShow() {
        ToolRun run = new ToolRun("ada@example.com", 5);

        new AgentToolCallback(new RecordingTool("propose_order"), true).call("{}", contextFor(run));

        assertThat(run.proposals()).containsExactly("{\"id\": \"proposal-1\"}");
    }

    private static ToolContext contextFor(ToolRun run) {
        return new ToolContext(Map.of(ToolRun.CONTEXT_KEY, run));
    }
}
