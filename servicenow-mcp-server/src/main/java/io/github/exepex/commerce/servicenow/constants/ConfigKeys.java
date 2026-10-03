package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The settings in {@code application.yml} that the code reads by name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    public static final String SERVICENOW_PREFIX = "commerce.servicenow";
    public static final String INCIDENTS_TOPIC = "${commerce.topics.incidents}";
    public static final String POLL_INTERVAL = "${commerce.servicenow.poll-interval}";
}
