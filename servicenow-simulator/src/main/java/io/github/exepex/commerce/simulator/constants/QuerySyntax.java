package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The parts of an encoded query the simulator understands, such as {@code assigned_toISEMPTY^state=1}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuerySyntax {

    /** Splits a query into its terms; a regular expression. */
    public static final String TERM_SEPARATOR = "\\^";
    public static final String ORDER_BY_DESCENDING = "ORDERBYDESC";
    public static final String ORDER_BY = "ORDERBY";
    public static final String IS_NOT_EMPTY = "ISNOTEMPTY";
    public static final String IS_EMPTY = "ISEMPTY";
    public static final String NOT_IN = "NOT IN";
    public static final String LIST_SEPARATOR = ",";
}
