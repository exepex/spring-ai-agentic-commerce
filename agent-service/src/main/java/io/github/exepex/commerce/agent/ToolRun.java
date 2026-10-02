package io.github.exepex.commerce.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * The state of one agent run, passed to every tool call through Spring AI's tool context: whose conversation it is,
 * which orders it may change, how many tool calls are left, and the results of the calls that succeeded. The model
 * never sees any of it.
 */
public final class ToolRun {

    public static final String CONTEXT_KEY = "commerce.toolRun";

    private final String customerEmail;
    private final String workId;
    private final Set<String> changeableOrders;
    private final BooleanSupplier workStillOwned;
    private final AtomicInteger callsLeft;
    /** One successful tool call: the tool, the arguments it was called with, and what it returned. */
    public record ToolResult(String tool, String arguments, String result) {}

    private final List<ToolResult> succeeded = new ArrayList<>();

    public ToolRun(String customerEmail, int callBudget) {
        this(customerEmail, callBudget, null, null, () -> true);
    }

    /**
     * @param workId what this run was started for, such as an incident number; null for a conversation
     * @param changeableOrders the only orders this run may change, possibly none; {@code null} when the run is not
     *     limited to particular orders
     * @param workStillOwned asked before every order change: whether the work this run was started for, such as an
     *     incident, is still this agent's
     */
    public ToolRun(String customerEmail, int callBudget, String workId, Set<String> changeableOrders,
            BooleanSupplier workStillOwned) {
        this.customerEmail = customerEmail;
        this.workId = workId;
        this.workStillOwned = workStillOwned;
        this.changeableOrders = changeableOrders == null ? null
                : changeableOrders.stream().map(ToolRun::normalized).collect(Collectors.toUnmodifiableSet());
        this.callsLeft = new AtomicInteger(callBudget);
    }

    /** Whether this run may change the given order. */
    boolean mayChange(String orderId) {
        return changeableOrders == null || orderId != null && changeableOrders.contains(normalized(orderId));
    }

    /** Whether the work this run was started for is still this agent's. */
    boolean workStillOwned() {
        return workStillOwned.getAsBoolean();
    }

    /** An order id as the shop reads it: a UUID, whatever its case or surrounding spaces. */
    private static String normalized(String orderId) {
        return orderId.strip().toLowerCase(Locale.ROOT);
    }

    String customerEmail() {
        return customerEmail;
    }

    /** Whether this run may work the given incident: only the one it was started for, if any. */
    boolean mayWorkIncident(String number) {
        return workId == null || workId.equals(number == null ? null : number.strip());
    }

    boolean takeCall() {
        return callsLeft.getAndDecrement() > 0;
    }

    synchronized void recordSuccess(String tool, String arguments, String result) {
        succeeded.add(new ToolResult(tool, arguments, result));
    }

    public synchronized List<ToolResult> succeeded() {
        return List.copyOf(succeeded);
    }

    public synchronized List<String> proposals() {
        return succeeded.stream().filter(call -> "propose_order".equals(call.tool())).map(ToolResult::result).toList();
    }
}
