package io.github.exepex.commerce.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The state of one agent run, passed to every tool call through Spring AI's tool context: whose conversation it is,
 * how many tool calls are left, and the results of the calls that succeeded. The model never sees any of it.
 */
public final class ToolRun {

    public static final String CONTEXT_KEY = "commerce.toolRun";

    private final String customerEmail;
    private final AtomicInteger callsLeft;
    /** One successful tool call: the tool, the arguments it was called with, and what it returned. */
    public record ToolResult(String tool, String arguments, String result) {}

    private final List<ToolResult> succeeded = new ArrayList<>();

    public ToolRun(String customerEmail, int callBudget) {
        this.customerEmail = customerEmail;
        this.callsLeft = new AtomicInteger(callBudget);
    }

    String customerEmail() {
        return customerEmail;
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
