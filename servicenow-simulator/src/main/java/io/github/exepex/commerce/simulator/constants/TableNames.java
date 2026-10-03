package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The ServiceNow tables the simulator keeps. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TableNames {

    public static final String INCIDENT = "incident";
    public static final String JOURNAL = "sys_journal_field";
    public static final String USER = "sys_user";
    public static final String GROUP = "sys_user_group";
}
