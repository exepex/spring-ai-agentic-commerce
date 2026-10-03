package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The fields of a ServiceNow incident event that wakes the incident agent. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IncidentEventFields {

    public static final String NUMBER = "number";
    public static final String ORDER_ID = "orderId";
}
