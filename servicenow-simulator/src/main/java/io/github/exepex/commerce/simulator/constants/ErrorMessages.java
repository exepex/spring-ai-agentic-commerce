package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the simulator tells its callers when it cannot do what they asked. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String INVALID_TABLE = "Invalid table %s";
    public static final String INCIDENT_NOT_FOUND = "No incident %s";
    public static final String NAMED_RECORD_NOT_FOUND = "No %s named '%s'";
    public static final String INVALID_QUERY_TERM = "The simulator does not understand the query term '%s'";
    /** What a real instance answers a request without the integration user's login. */
    public static final String NOT_AUTHENTICATED =
            "{\"error\": {\"message\": \"User Not Authenticated\"}, \"status\": \"failure\"}";
}
