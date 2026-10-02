package io.github.exepex.commerce.servicenow.governance;

/**
 * A tool call the rules refuse; its message goes back to the agent and says what to do instead. It starts with
 * {@value #PREFIX}, so a caller can tell a refusal, which asking again will not change, from a failure such as
 * ServiceNow being down.
 */
public class ToolRefusedException extends RuntimeException {

    public static final String PREFIX = "Refused: ";

    public ToolRefusedException(String message) {
        super(PREFIX + message);
    }
}
