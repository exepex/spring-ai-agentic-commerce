package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.McpValues;
import io.github.exepex.commerce.agent.constants.Refusals;
import io.github.exepex.commerce.agent.constants.ToolNames;
import io.github.exepex.commerce.agent.constants.ToolParameters;
import io.github.exepex.commerce.agent.exception.ToolCalledOutsideRunException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.node.ObjectNode;

/**
 * Wraps an MCP tool before the model sees it. These governance rules live here, in code rather than in the prompt:
 *
 * <ul>
 *   <li><b>Identity is not the model's choice.</b> For a customer-facing agent the {@code customerEmail} parameter is
 *       removed from the schema the model sees and filled in from the signed-in customer on every call.</li>
 *   <li><b>Every run has a tool-call budget.</b> Once it is spent, further calls are refused, so a confused agent
 *       cannot loop.</li>
 *   <li><b>A run that works one order changes only that order.</b> An incident run may only cancel, refund or notify
 *       about the order linked to its incident, whatever the incident's text asks for, and only while the incident is
 *       still the agent's and still linked to that order.</li>
 *   <li><b>A run that works one incident works only that incident.</b> Its ServiceNow tools refuse any other
 *       incident number, so text in an incident or a hand-off cannot steer it to someone else's incident.</li>
 *   <li><b>One message per piece of work.</b> A message to the customer in an incident run carries a key made of the
 *       order and the incident, set by code, so an incident delivered again does not tell the customer twice.</li>
 *   <li><b>A switched-off agent stops.</b> A third-party MCP server, such as Slack's, cannot enforce the kill switch,
 *       so its tools check the switch before every call. The commerce MCP server enforces it itself.</li>
 * </ul>
 */
final class AgentToolCallback implements ToolCallback {

    /** The tools that change an order or tell its customer something. */
    private static final Set<String> ORDER_CHANGING_TOOLS =
            Set.of(ToolNames.CANCEL_ORDER, ToolNames.ISSUE_REFUND, ToolNames.NOTIFY_CUSTOMER);
    /** The ServiceNow tools that act on one incident, named by its number. */
    private static final Set<String> INCIDENT_TOOLS = Set.of(ToolNames.GET_INCIDENT, ToolNames.ADD_WORK_NOTE,
            ToolNames.ASSIGN_TO_TEAM, ToolNames.RESOLVE_INCIDENT);

    private final ToolCallback mcpTool;
    private final boolean injectsCustomer;
    private final BooleanSupplier agentSwitchedOn;
    private final ToolDefinition definition;

    AgentToolCallback(ToolCallback mcpTool, boolean injectsCustomer, BooleanSupplier agentSwitchedOn) {
        this.mcpTool = mcpTool;
        this.injectsCustomer = injectsCustomer;
        this.agentSwitchedOn = agentSwitchedOn;
        var original = mcpTool.getToolDefinition();
        var setByCode = new HashSet<String>();
        if (injectsCustomer) {
            setByCode.add(ToolParameters.CUSTOMER_EMAIL);
        }
        if (ToolNames.NOTIFY_CUSTOMER.equals(original.name())) {
            setByCode.add(ToolParameters.IDEMPOTENCY_KEY);
        }
        if (ToolNames.ISSUE_REFUND.equals(original.name())) {
            setByCode.add(ToolParameters.INCIDENT_NUMBER);
        }
        this.definition = setByCode.isEmpty() ? original : ToolJson.without(setByCode, original);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        throw new ToolCalledOutsideRunException();
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        var run = (ToolRun) toolContext.getContext().get(McpValues.TOOL_RUN_CONTEXT_KEY);
        return refusal(run, toolInput).orElseGet(() -> callMcpTool(run, withArgumentsSetByCode(run, toolInput)));
    }

    /** Why the call may not go ahead, if it may not. Every call, refused or not, spends one call of the budget. */
    private Optional<String> refusal(ToolRun run, String toolInput) {
        if (!run.takeCall()) {
            return Optional.of(Refusals.BUDGET_SPENT);
        }
        if (run.pastDeadline()) {
            return Optional.of(Refusals.TIME_SPENT);
        }
        if (INCIDENT_TOOLS.contains(definition.name())) {
            var number = ToolJson.argument(toolInput, ToolParameters.NUMBER);
            if (!run.mayWorkIncident(number)) {
                return Optional.of(Refusals.OTHER_INCIDENT.formatted(number));
            }
        }
        if (ORDER_CHANGING_TOOLS.contains(definition.name())) {
            var orderId = ToolJson.argument(toolInput, ToolParameters.ORDER_ID);
            if (!run.mayChange(orderId)) {
                return Optional.of(Refusals.OTHER_ORDER.formatted(orderId));
            }
            if (!run.workStillAllows(orderId)) {
                return Optional.of(Refusals.WORK_NO_LONGER_ALLOWS);
            }
        }
        if (!agentSwitchedOn.getAsBoolean()) {
            return Optional.of(Refusals.SWITCHED_OFF);
        }
        return Optional.empty();
    }

    /** The arguments the MCP server receives: whatever the model passed for the parameters code sets is replaced. */
    private String withArgumentsSetByCode(ToolRun run, String toolInput) {
        var notifies = ToolNames.NOTIFY_CUSTOMER.equals(definition.name());
        var refunds = ToolNames.ISSUE_REFUND.equals(definition.name());
        if (!injectsCustomer && !notifies && !refunds) {
            return toolInput;
        }
        var arguments = (ObjectNode) ToolJson.arguments(toolInput);
        if (injectsCustomer) {
            arguments.put(ToolParameters.CUSTOMER_EMAIL, run.customerEmail());
        }
        if (notifies) {
            arguments.remove(ToolParameters.IDEMPOTENCY_KEY);
            var key = run.notificationKeyFor(arguments.path(ToolParameters.ORDER_ID).asString(""));
            if (key != null) {
                arguments.put(ToolParameters.IDEMPOTENCY_KEY, key);
            }
        }
        if (refunds) {
            arguments.remove(ToolParameters.INCIDENT_NUMBER);
            if (run.workId() != null) {
                arguments.put(ToolParameters.INCIDENT_NUMBER, run.workId());
            }
        }
        return ToolJson.write(arguments);
    }

    private String callMcpTool(ToolRun run, String input) {
        // A call the MCP server refused throws here, so only successful calls are recorded.
        var result = mcpTool.call(input);
        run.recordSuccess(definition.name(), input, McpResults.textOf(result));
        return result;
    }
}
