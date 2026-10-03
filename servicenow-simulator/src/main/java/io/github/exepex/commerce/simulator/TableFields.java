package io.github.exepex.commerce.simulator;

import io.github.exepex.commerce.simulator.constants.FieldNames;
import io.github.exepex.commerce.simulator.constants.IncidentStates;
import io.github.exepex.commerce.simulator.constants.ServiceNowValues;
import io.github.exepex.commerce.simulator.constants.TableNames;
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
    private static final Map<String, String> REFERENCES = Map.of(FieldNames.ASSIGNMENT_GROUP, TableNames.GROUP,
            FieldNames.ASSIGNED_TO, TableNames.USER, FieldNames.CALLER_ID, TableNames.USER);
    private static final Map<String, String> STATE_NAMES = Map.of(
            IncidentStates.NEW, IncidentStates.NEW_NAME,
            IncidentStates.IN_PROGRESS, IncidentStates.IN_PROGRESS_NAME,
            IncidentStates.ON_HOLD, IncidentStates.ON_HOLD_NAME,
            IncidentStates.RESOLVED, IncidentStates.RESOLVED_NAME,
            IncidentStates.CLOSED, IncidentStates.CLOSED_NAME,
            IncidentStates.CANCELED, IncidentStates.CANCELED_NAME);
    /** Text fields that add a journal entry rather than being stored on the incident. */
    private static final List<String> JOURNAL_FIELDS = List.of(FieldNames.WORK_NOTES, FieldNames.COMMENTS);

    /** Whether the field points at a row of another table, such as an incident's assignment group. */
    static boolean isReference(String table, String field) {
        return TableNames.INCIDENT.equals(table) && REFERENCES.containsKey(field);
    }

    /** The table an incident's reference field points into; null for a field that is no reference. */
    static String referencedTable(String field) {
        return REFERENCES.get(field);
    }

    /** Whether the field is a journal field, such as work notes, shown on the incident but kept as journal entries. */
    static boolean isJournal(String table, String field) {
        return TableNames.INCIDENT.equals(table) && isJournalField(field);
    }

    /** Whether setting the incident field adds a journal entry instead of storing a value. */
    static boolean isJournalField(String field) {
        return JOURNAL_FIELDS.contains(field);
    }

    /** What a journal entry of the field is called where the incident shows it. */
    static String journalKind(String field) {
        return FieldNames.WORK_NOTES.equals(field) ? ServiceNowValues.WORK_NOTES_KIND : ServiceNowValues.COMMENTS_KIND;
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
        return Arrays.stream(userName.split(ServiceNowValues.USER_NAME_SEPARATORS))
                .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((first, second) -> first + ServiceNowValues.NAME_SEPARATOR + second)
                .orElse(userName);
    }
}
