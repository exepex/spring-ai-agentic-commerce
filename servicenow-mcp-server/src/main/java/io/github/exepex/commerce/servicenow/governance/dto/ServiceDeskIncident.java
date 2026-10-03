package io.github.exepex.commerce.servicenow.governance.dto;

import java.util.UUID;

/** An incident the service desk raised about an order, and who has it; {@code status} is WITH_AGENT or WITH_TEAM. */
public record ServiceDeskIncident(UUID orderId, String number, String url, String shortDescription, String status,
        String assignmentGroup) {}
