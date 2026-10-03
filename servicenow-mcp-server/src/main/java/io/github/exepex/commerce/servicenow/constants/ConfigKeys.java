package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The settings in {@code application.yml} that the code reads by name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    public static final String COMMERCE_PREFIX = "commerce";
    public static final String SERVICENOW_PREFIX = "commerce.servicenow";
    public static final String INCIDENTS_TOPIC = "${commerce.topics.incidents}";
    public static final String POLL_INTERVAL = "${commerce.servicenow.poll-interval}";
    /** The HTTP service group of the governance API, configured under {@code spring.http.serviceclient}. */
    public static final String GOVERNANCE_CLIENT = "governance";
}
