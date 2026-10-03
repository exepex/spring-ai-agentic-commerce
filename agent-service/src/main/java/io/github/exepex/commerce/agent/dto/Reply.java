package io.github.exepex.commerce.agent.dto;

import java.util.List;

/** The shopping assistant's answer, and the orders it proposed for the customer to confirm, as JSON. */
public record Reply(String text, List<String> proposals) {}
