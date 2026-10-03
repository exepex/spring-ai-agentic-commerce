package io.github.exepex.commerce.simulator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What the simulated incident table's fields are: which point at another table's row, which are journal fields kept
 * as entries of their own, and what each state is called.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TableFields {

    /** The fields that point at another table's row, and that table. */
    private static final Map<String, String> REFERENCES = Map.of("assignment_group", Tables.GROUP,
            "assigned_to", Tables.USER, "caller_id", Tables.USER);
    private static final Map<String, String> STATE_NAMES = Map.of("1", "New", "2", "In Progress", "3", "On Hold",
            "6", "Resolved", "7", "Closed", "8", "Canceled");
    /** Text fields that add a journal entry rather than being stored on the incident. */
    private static final List<String> JOURNAL_FIELDS = List.of("work_notes", "comments");

    /** Whether the field points at a row of another table, such as an incident's assignment group. */
    static boolean isReference(String table, String field) {
        return Tables.INCIDENT.equals(table) && REFERENCES.containsKey(field);
    }

    /** The table an incident's reference field points into; null for a field that is no reference. */
    static String referencedTable(String field) {
        return REFERENCES.get(field);
    }

    /** Whether the field is a journal field, such as work notes, shown on the incident but kept as journal entries. */
    static boolean isJournal(String table, String field) {
        return Tables.INCIDENT.equals(table) && isJournalField(field);
    }

    /** Whether setting the incident field adds a journal entry instead of storing a value. */
    static boolean isJournalField(String field) {
        return JOURNAL_FIELDS.contains(field);
    }

    /** What a journal entry of the field is called where the incident shows it. */
    static String journalKind(String field) {
        return "work_notes".equals(field) ? "Work notes" : "Additional comments";
    }

    /** A state's name, such as In Progress; the code itself for a state the simulator does not know. */
    static String stateName(String code) {
        return STATE_NAMES.getOrDefault(code, code);
    }

    /** The code of a state given by its name, in any case; empty for a name the simulator does not know. */
    static Optional<String> stateCode(String name) {
        return STATE_NAMES.entrySet().stream().filter(state -> state.getValue().equalsIgnoreCase(name))
                .map(Map.Entry::getKey).findFirst();
    }

    /** A user's name as a person sees it, made from the user name: {@code agent.user} is Agent User. */
    static String personName(String userName) {
        return Arrays.stream(userName.split("[._]"))
                .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((first, second) -> first + " " + second)
                .orElse(userName);
    }
}
