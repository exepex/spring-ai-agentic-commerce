package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The agents' identities, as used in their definitions, tokens, the audit trail and the kill switches. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentIds {

    public static final String SHOPPING_ASSISTANT = "shopping-assistant";
    public static final String INCIDENT_AGENT = "incident-agent";
}
