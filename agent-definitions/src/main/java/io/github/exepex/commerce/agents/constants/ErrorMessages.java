package io.github.exepex.commerce.agents.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Why the agent definitions cannot be used; each stops the service that reads them from starting. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String DEFINITIONS_UNREADABLE = "Could not read the agent definitions";
    public static final String DEFINED_TWICE = "Agent %s is defined twice";
    public static final String NO_SUCH_AGENT = "No agent is defined with id %s";

    /** A definition file's name followed by what is wrong with it. */
    public static final String INVALID_DEFINITION = "%s %s";
    public static final String NO_HEADER = "must start with a YAML header between --- lines";
    public static final String MISSING_SETTING = "is missing '%s'";
    public static final String EMPTY_SETTING = "has an empty %s";
    public static final String NO_TOOLS = "lists no '%s' tools";
    public static final String SETTING = "'%s'";
    public static final String INSTRUCTIONS = "the instructions";
}
