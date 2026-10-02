package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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

        /** Answers like Spring AI's MCP tool callback: the JSON of the result's content list. */
        @Override
        public String call(String toolInput) {
            inputs.add(toolInput);
            return "[{\"text\":\"{\\\"id\\\": \\\"proposal-1\\\"}\"}]";
        }
    }

    @Test
    void hidesTheCustomerFromTheModelAndFillsInTheSignedInCustomer() {
        RecordingTool mcpTool = new RecordingTool("get_order");
        AgentToolCallback tool = new AgentToolCallback(mcpTool, true, () -> true);

        assertThat(tool.getToolDefinition().inputSchema()).doesNotContain("customerEmail").contains("orderId");

        tool.call("{\"orderId\": \"o-1\", \"customerEmail\": \"someone-else@example.com\"}",
                contextFor(new ToolRun("ada@example.com", 5)));

        assertThat(mcpTool.inputs.getFirst()).contains("\"customerEmail\":\"ada@example.com\"").doesNotContain("someone-else");
    }

    @Test
    void leavesTheSchemaAloneForAnAgentThatIsNotCustomerFacing() {
        AgentToolCallback tool = new AgentToolCallback(new RecordingTool("get_order"), false, () -> true);

        assertThat(tool.getToolDefinition().inputSchema()).contains("customerEmail");
    }

    @Test
    void refusesCallsOnceTheRunHasSpentItsBudget() {
        RecordingTool mcpTool = new RecordingTool("get_order");
        AgentToolCallback tool = new AgentToolCallback(mcpTool, false, () -> true);
        ToolContext context = contextFor(new ToolRun(null, 2));

        tool.call("{}", context);
        tool.call("{}", context);
        String third = tool.call("{}", context);

        assertThat(third).startsWith("Refused");
        assertThat(mcpTool.inputs).hasSize(2);
    }

    @Test
    void refusesAThirdPartyToolOnceTheAgentIsSwitchedOffDuringTheRun() {
        RecordingTool slackTool = new RecordingTool("conversations_add_message");
        AtomicBoolean switchedOn = new AtomicBoolean(true);
        AgentToolCallback tool = new AgentToolCallback(slackTool, false, switchedOn::get);
        ToolContext context = contextFor(new ToolRun(null, 5));

        tool.call("{}", context);
        switchedOn.set(false);
        String afterSwitchOff = tool.call("{}", context);

        assertThat(afterSwitchOff).startsWith("Refused").contains("switched off");
        assertThat(slackTool.inputs).hasSize(1);
    }

    @Test
    void aRunLimitedToOneOrderMayChangeOnlyThatOrder() {
        String linkedOrder = "0b6f2a3e-5d1c-4c1e-9a7b-2f1d3c4b5a69";
        RecordingTool refund = new RecordingTool("issue_refund");
        AgentToolCallback tool = new AgentToolCallback(refund, false, () -> true);
        ToolContext context = contextFor(new ToolRun(null, 5, null, Set.of(linkedOrder), orderId -> true));

        String otherOrder = tool.call("{\"orderId\": \"7c9e6679-7425-40de-944b-e07fc1f90ae7\"}", context);
        String sameOrderInCapitals = tool.call("{\"orderId\": \" " + linkedOrder.toUpperCase() + "\"}", context);

        assertThat(otherOrder).startsWith("Refused").contains("only change the order linked to its incident");
        assertThat(sameOrderInCapitals).doesNotStartWith("Refused");
        assertThat(refund.inputs).hasSize(1);
    }

    @Test
    void aRunWithNoLinkedOrderMayReadOrdersButChangeNone() {
        ToolContext context = contextFor(new ToolRun(null, 5, null, Set.of(), orderId -> true));

        String lookup = new AgentToolCallback(new RecordingTool("get_order"), false, () -> true)
                .call("{\"orderId\": \"o-1\"}", context);
        String cancel = new AgentToolCallback(new RecordingTool("cancel_order"), false, () -> true)
                .call("{\"orderId\": \"o-1\"}", context);
        String notify = new AgentToolCallback(new RecordingTool("notify_customer"), false, () -> true).call("{}", context);

        assertThat(lookup).doesNotStartWith("Refused");
        assertThat(cancel).startsWith("Refused");
        assertThat(notify).startsWith("Refused");
    }

    @Test
    void aRunWhoseIncidentWasTakenOverChangesNoOrder() {
        String linkedOrder = "0b6f2a3e-5d1c-4c1e-9a7b-2f1d3c4b5a69";
        AtomicBoolean stillOwned = new AtomicBoolean(true);
        RecordingTool cancel = new RecordingTool("cancel_order");
        AgentToolCallback tool = new AgentToolCallback(cancel, false, () -> true);
        ToolContext context = contextFor(new ToolRun(null, 5, null, Set.of(linkedOrder), orderId -> stillOwned.get()));

        stillOwned.set(false);
        String afterTakeOver = tool.call("{\"orderId\": \"" + linkedOrder + "\"}", context);
        String lookup = new AgentToolCallback(new RecordingTool("get_order"), false, () -> true)
                .call("{\"orderId\": \"" + linkedOrder + "\"}", context);

        assertThat(afterTakeOver).startsWith("Refused").contains("no longer this agent's");
        assertThat(cancel.inputs).isEmpty();
        assertThat(lookup).doesNotStartWith("Refused");
    }

    @Test
    void anIncidentRunsMessageToTheCustomerCarriesAKeyThatCodeSets() {
        String linkedOrder = "0b6f2a3e-5d1c-4c1e-9a7b-2f1d3c4b5a69";
        RecordingTool notify = new RecordingTool("notify_customer");
        AgentToolCallback tool = new AgentToolCallback(notify, false, () -> true);
        ToolContext incidentRun = contextFor(new ToolRun(null, 5, "INC0010001", Set.of(linkedOrder), orderId -> true));

        tool.call("{\"orderId\": \"" + linkedOrder + "\", \"message\": \"Sorry\", \"idempotencyKey\": \"mine\"}",
                incidentRun);
        RecordingTool notifyOutsideAnIncident = new RecordingTool("notify_customer");
        new AgentToolCallback(notifyOutsideAnIncident, false, () -> true)
                .call("{\"orderId\": \"o-1\", \"idempotencyKey\": \"mine\"}", contextFor(new ToolRun(null, 5)));

        assertThat(JsonPath.<String>read(notify.inputs.getFirst(), "$.idempotencyKey"))
                .isEqualTo("notify-" + linkedOrder + "-INC0010001");
        assertThat(notifyOutsideAnIncident.inputs.getFirst()).doesNotContain("idempotencyKey");
        assertThat(tool.getToolDefinition().inputSchema()).doesNotContain("idempotencyKey");
    }

    @Test
    void collectsOrderProposalsForTheChatToShow() {
        ToolRun run = new ToolRun("ada@example.com", 5);

        new AgentToolCallback(new RecordingTool("propose_order"), true, () -> true).call("{}", contextFor(run));

        assertThat(run.proposals()).containsExactly("{\"id\": \"proposal-1\"}");
    }

    private static ToolContext contextFor(ToolRun run) {
        return new ToolContext(Map.of(ToolRun.CONTEXT_KEY, run));
    }
}
