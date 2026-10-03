package io.github.exepex.commerce.simulator;

import io.github.exepex.commerce.simulator.constants.QuerySyntax;
import io.github.exepex.commerce.simulator.exception.InvalidEncodedQueryException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * An encoded query such as {@code assigned_toISEMPTY^state=1^ORDERBYDESCsys_created_on}, as far as the simulator
 * understands one: the terms a row must match, and the field its rows are ordered by.
 *
 * @param terms the terms every matching row meets
 * @param orderBy the field the rows are ordered by; null to keep the order they were added in
 * @param descending whether the order is turned round, newest first
 */
record EncodedQuery(List<Term> terms, String orderBy, boolean descending) {

    private static final Pattern IN = Pattern.compile(QuerySyntax.IN);

    /** One term: the field it reads, and which of that field's stored values it accepts. */
    record Term(String field, Predicate<String> accepts) {}

    /** @throws InvalidEncodedQueryException for a term the simulator does not understand */
    static EncodedQuery parse(String encodedQuery) {
        var terms = new ArrayList<Term>();
        String orderBy = null;
        var descending = false;
        for (var term : encodedQuery == null || encodedQuery.isBlank() ? new String[0]
                : encodedQuery.split(QuerySyntax.TERM_SEPARATOR)) {
            if (term.startsWith(QuerySyntax.ORDER_BY_DESCENDING)) {
                orderBy = term.substring(QuerySyntax.ORDER_BY_DESCENDING.length());
                descending = true;
            } else if (term.startsWith(QuerySyntax.ORDER_BY)) {
                orderBy = term.substring(QuerySyntax.ORDER_BY.length());
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
        if (term.endsWith(QuerySyntax.IS_NOT_EMPTY)) {
            return new Term(term.substring(0, term.length() - QuerySyntax.IS_NOT_EMPTY.length()),
                    value -> !value.isEmpty());
        }
        if (term.endsWith(QuerySyntax.IS_EMPTY)) {
            return new Term(term.substring(0, term.length() - QuerySyntax.IS_EMPTY.length()), String::isEmpty);
        }
        var notIn = term.indexOf(QuerySyntax.NOT_IN);
        if (notIn > 0) {
            var excluded = Arrays.asList(term.substring(notIn + QuerySyntax.NOT_IN.length())
                    .split(QuerySyntax.LIST_SEPARATOR));
            return new Term(term.substring(0, notIn), value -> !excluded.contains(value));
        }
        var in = IN.matcher(term);
        if (in.matches()) {
            var included = Arrays.asList(in.group(2).split(QuerySyntax.LIST_SEPARATOR));
            return new Term(in.group(1), included::contains);
        }
        var equals = term.indexOf('=');
        if (equals < 0) {
            throw new InvalidEncodedQueryException(term);
        }
        var expected = term.substring(equals + 1);
        return new Term(term.substring(0, equals), expected::equals);
    }
}
