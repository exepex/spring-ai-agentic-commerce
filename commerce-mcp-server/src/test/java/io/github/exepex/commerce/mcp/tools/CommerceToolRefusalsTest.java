package io.github.exepex.commerce.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exepex.commerce.mcp.exception.CustomerScopeViolationException;
import io.github.exepex.commerce.mcp.exception.DownstreamException;
import io.github.exepex.commerce.mcp.exception.IdempotencyKeyReusedException;
import io.github.exepex.commerce.mcpserver.guard.StoppedCallOutcome;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Which stopped tool calls a rule denied, and how a refusal reads to the model. */
class CommerceToolRefusalsTest {

    private final CommerceToolRefusals refusals = new CommerceToolRefusals();

    @Test
    void aRuleThatForbidsTheCallDeniesIt() {
        assertThat(refusals.outcomeOf(new CustomerScopeViolationException(UUID.randomUUID())))
                .isEqualTo(StoppedCallOutcome.DENIED);
    }

    @Test
    void aCommerceServiceThatForbidsTheCallMakesItFailNotDenied() {
        var forbidden = DownstreamException.refused(HttpStatus.FORBIDDEN, "Forbidden", Map.of(), null);

        assertThat(refusals.outcomeOf(forbidden)).isEqualTo(StoppedCallOutcome.FAILED);
        assertThat(refusals.outcomeOf(DownstreamException.unreachable("payment service", null)))
                .isEqualTo(StoppedCallOutcome.FAILED);
    }

    @Test
    void anyOtherRuleOrErrorThatStopsTheCallMakesItFail() {
        assertThat(refusals.outcomeOf(new IdempotencyKeyReusedException("refund-1"))).isEqualTo(StoppedCallOutcome.FAILED);
        assertThat(refusals.outcomeOf(new IllegalStateException("boom"))).isEqualTo(StoppedCallOutcome.FAILED);
    }

    @Test
    void refusalsTellTheModelWhatToDo() {
        assertThat(refusals.notPermitted("incident-agent", "propose_order"))
                .hasMessage("Agent incident-agent is not permitted to call propose_order");
        assertThat(refusals.switchedOff("shopping-assistant")).hasMessage("Agent shopping-assistant is switched off. "
                + "Stop, and hand any work that needs doing to a human with escalate_to_human.");
    }
}
