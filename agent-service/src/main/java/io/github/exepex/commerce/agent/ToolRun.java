package io.github.exepex.commerce.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
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
    private final Predicate<String> workStillAllows;
    private final AtomicInteger callsLeft;
    /** One successful tool call: the tool, the arguments it was called with, and what it returned. */
    public record ToolResult(String tool, String arguments, String result) {}

    private final List<ToolResult> succeeded = new ArrayList<>();

    public ToolRun(String customerEmail, int callBudget) {
        this(customerEmail, callBudget, null, null, orderId -> true);
    }

    /**
     * @param workId what this run was started for, such as an incident number; null for a conversation
     * @param changeableOrders the only orders this run may change, possibly none; {@code null} when the run is not
     *     limited to particular orders
     * @param workStillAllows asked before every order change, with the order: whether the work this run was started
     *     for, such as an incident, is still this agent's and still about that order
     */
    public ToolRun(String customerEmail, int callBudget, String workId, Set<String> changeableOrders,
            Predicate<String> workStillAllows) {
        this.customerEmail = customerEmail;
        this.workId = workId;
        this.workStillAllows = workStillAllows;
        this.changeableOrders = changeableOrders == null ? null
                : changeableOrders.stream().map(ToolRun::normalized).collect(Collectors.toUnmodifiableSet());
        this.callsLeft = new AtomicInteger(callBudget);
    }

    /** Whether this run may change the given order. */
    boolean mayChange(String orderId) {
        return changeableOrders == null || orderId != null && changeableOrders.contains(normalized(orderId));
    }

    /** Whether the work this run was started for is still this agent's, and still lets it change the order. */
    boolean workStillAllows(String orderId) {
        return workStillAllows.test(orderId);
    }

    /** An order id as the shop reads it: a UUID, whatever its case or surrounding spaces. */
    private static String normalized(String orderId) {
        return orderId.strip().toLowerCase(Locale.ROOT);
    }

    String customerEmail() {
        return customerEmail;
    }

    /**
     * The key that makes a message to the order's customer go out once for this run's work, however often the work is
     * delivered again; null for a run that is not about one piece of work.
     */
    String notificationKeyFor(String orderId) {
        return workId == null ? null : "notify-" + normalized(orderId) + "-" + workId;
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
