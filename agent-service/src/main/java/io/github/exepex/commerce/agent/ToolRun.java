package io.github.exepex.commerce.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The state of one agent run, passed to every tool call through Spring AI's tool context: whose conversation it is,
 * how many tool calls are left, and the order proposals made so far. The model never sees any of it.
 */
public final class ToolRun {

    public static final String CONTEXT_KEY = "commerce.toolRun";

    private final String customerEmail;
    private final AtomicInteger callsLeft;
    private final List<String> proposals = new ArrayList<>();

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

    synchronized void recordProposal(String proposalJson) {
        proposals.add(proposalJson);
    }

    public synchronized List<String> proposals() {
        return List.copyOf(proposals);
    }
}
