package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles confirmations that did not finish: the order service did not answer, the order's payment was still pending,
 * or this server stopped half way. Each one places the same order again, which is idempotent. A confirmation only
 * counts as stalled once it is older than {@code commerce.reconciliation.settle-after}, so one still running is left
 * alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProposalReconciler {

    private final OrderProposalRepository proposals;
    private final ProposalService proposalService;
    private final Clock clock;

    @Value(ConfigKeys.RECONCILIATION_SETTLE_AFTER)
    private final Duration settleAfter;

    @Scheduled(fixedDelayString = ConfigKeys.RECONCILIATION_INTERVAL,
            initialDelayString = ConfigKeys.RECONCILIATION_INTERVAL)
    public void reconcile() {
        for (var proposal : proposals.findByStatusAndConfirmingSinceBefore(OrderProposal.Status.CONFIRMING,
                Instant.now(clock).minus(settleAfter))) {
            try {
                proposalService.settle(proposal);
            } catch (RuntimeException failure) {
                log.warn("Could not settle the confirmation of proposal {}; it will be retried", proposal.getId(), failure);
            }
        }
    }
}
