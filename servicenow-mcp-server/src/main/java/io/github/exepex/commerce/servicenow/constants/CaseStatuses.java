package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Who has a case's incident, as the governance API takes it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CaseStatuses {

    public static final String WITH_AGENT = "WITH_AGENT";
    public static final String WITH_TEAM = "WITH_TEAM";
    public static final String RESOLVED = "RESOLVED";
}
