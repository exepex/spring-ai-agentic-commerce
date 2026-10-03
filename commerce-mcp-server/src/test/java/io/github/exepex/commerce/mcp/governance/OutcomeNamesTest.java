package io.github.exepex.commerce.mcp.governance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exepex.commerce.governance.api.dto.ToolCallOutcome;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Each outcome an agent or the governed tool call reports is recorded under the audit outcome of the same name. */
class OutcomeNamesTest {

    @Test
    void everyReportedOutcomeIsAnAuditOutcome() {
        assertThat(Arrays.stream(ToolCallOutcome.values()).map(outcome -> AuditEvent.Outcome.valueOf(outcome.name())))
                .hasSize(ToolCallOutcome.values().length);
        assertThat(Arrays.stream(io.github.exepex.commerce.mcpserver.guard.ToolCallOutcome.values())
                .map(outcome -> AuditEvent.Outcome.valueOf(outcome.name())))
                .containsExactly(AuditEvent.Outcome.DENIED, AuditEvent.Outcome.FAILED);
    }
}
