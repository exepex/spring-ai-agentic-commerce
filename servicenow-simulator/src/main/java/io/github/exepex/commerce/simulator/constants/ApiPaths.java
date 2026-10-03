package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The Table API's addresses, as a real instance serves them. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    /** Everything under it needs the integration user's login. */
    public static final String TABLE_API = "/api/now/";
    public static final String TABLE = TABLE_API + "table/{table}";
    public static final String RECORD = TABLE + "/{sysId}";
    public static final String INCIDENTS = TABLE_API + "table/" + TableNames.INCIDENT;
    public static final String INCIDENT = INCIDENTS + "/{sysId}";
}
