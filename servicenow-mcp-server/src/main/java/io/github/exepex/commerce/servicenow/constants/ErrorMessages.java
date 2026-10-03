package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the server reports when it cannot start or cannot do its work, other than a refused tool call. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String UNKNOWN_DEFAULT_TEAM =
            "commerce.servicenow.default-team must name one of the configured teams";
    public static final String INTEGRATION_USER_NOT_FOUND = "ServiceNow has no user %s";
}
