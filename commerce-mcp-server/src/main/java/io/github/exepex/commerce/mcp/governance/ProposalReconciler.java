package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.constants.JobLocks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

    /**
     * One instance at a time; the oldest confirmations first, 50 per run, so a backlog cannot swamp it and a run ends
     * well within its lock even when every call waits for its timeout.
     */
    @Scheduled(fixedDelayString = ConfigKeys.RECONCILIATION_INTERVAL,
            initialDelayString = ConfigKeys.RECONCILIATION_INTERVAL)
    @SchedulerLock(name = JobLocks.PROPOSAL_RECONCILIATION)
    public void reconcile() {
        for (var proposal : proposals.findTop50ByStatusAndConfirmingSinceBeforeOrderByConfirmingSince(
                OrderProposal.Status.CONFIRMING,
                Instant.now(clock).minus(settleAfter))) {
            try {
                proposalService.settle(proposal);
            } catch (RuntimeException failure) {
                log.warn("Could not settle the confirmation of proposal {}; it will be retried", proposal.getId(), failure);
            }
        }
    }
}
