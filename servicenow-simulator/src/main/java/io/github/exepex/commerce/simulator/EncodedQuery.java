package io.github.exepex.commerce.simulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * An encoded query such as {@code assigned_toISEMPTY^state=1^ORDERBYDESCsys_created_on}, as far as the simulator
 * understands one: the terms a row must match, and the field its rows are ordered by.
 *
 * @param terms the terms every matching row meets
 * @param orderBy the field the rows are ordered by; null to keep the order they were added in
 * @param descending whether the order is turned round, newest first
 */
record EncodedQuery(List<Term> terms, String orderBy, boolean descending) {

    /** One term: the field it reads, and which of that field's stored values it accepts. */
    record Term(String field, Predicate<String> accepts) {}

    /** @throws IllegalArgumentException for a term the simulator does not understand */
    static EncodedQuery parse(String encodedQuery) {
        List<Term> terms = new ArrayList<>();
        String orderBy = null;
        boolean descending = false;
        for (String term : encodedQuery == null || encodedQuery.isBlank() ? new String[0] : encodedQuery.split("\\^")) {
            if (term.startsWith("ORDERBYDESC")) {
                orderBy = term.substring("ORDERBYDESC".length());
                descending = true;
            } else if (term.startsWith("ORDERBY")) {
                orderBy = term.substring("ORDERBY".length());
            } else {
                terms.add(termOf(term));
            }
        }
        return new EncodedQuery(List.copyOf(terms), orderBy, descending);
    }

    /** Whether the row meets every term, each field's value read with {@code valueOf}. */
    boolean matches(Map<String, String> row, BiFunction<Map<String, String>, String, String> valueOf) {
        return terms.stream().allMatch(term -> term.accepts().test(valueOf.apply(row, term.field())));
    }

    /** Puts the found rows in the query's order, if it asks for one. */
    void order(List<Map<String, String>> found) {
        if (orderBy == null) {
            return;
        }
        // Sorted oldest first, keeping the order rows were added in for equal times, then turned round if asked.
        found.sort(Comparator.comparing(row -> row.getOrDefault(orderBy, "")));
        if (descending) {
            Collections.reverse(found);
        }
    }

    private static Term termOf(String term) {
        if (term.endsWith("ISNOTEMPTY")) {
            return new Term(term.substring(0, term.length() - "ISNOTEMPTY".length()), value -> !value.isEmpty());
        }
        if (term.endsWith("ISEMPTY")) {
            return new Term(term.substring(0, term.length() - "ISEMPTY".length()), String::isEmpty);
        }
        int notIn = term.indexOf("NOT IN");
        if (notIn > 0) {
            List<String> excluded = Arrays.asList(term.substring(notIn + "NOT IN".length()).split(","));
            return new Term(term.substring(0, notIn), value -> !excluded.contains(value));
        }
        int equals = term.indexOf('=');
        if (equals < 0) {
            throw new IllegalArgumentException("The simulator does not understand the query term '" + term + "'");
        }
        String expected = term.substring(equals + 1);
        return new Term(term.substring(0, equals), expected::equals);
    }
}
