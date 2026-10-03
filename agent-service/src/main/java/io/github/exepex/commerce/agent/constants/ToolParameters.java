package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The parameters of the MCP tools that code reads or sets, and the fields of their results that code reads. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolParameters {

    public static final String CUSTOMER_EMAIL = "customerEmail";
    public static final String ORDER_ID = "orderId";
    /** The parameter that makes a message go out once; set by code from the run's work, never by the model. */
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    /**
     * The incident a refund is for, which decides whether the incident agent may pay while the order has other cases;
     * set by code from the run's work, never by the model.
     */
    public static final String INCIDENT_NUMBER = "incidentNumber";
    public static final String NUMBER = "number";
    public static final String NOTE = "note";
    public static final String LINKED_ORDER_ID = "linkedOrderId";

    /** The idempotency key of a message to an order's customer: the order, then the work. */
    public static final String NOTIFICATION_KEY = "notify-%s-%s";
}
