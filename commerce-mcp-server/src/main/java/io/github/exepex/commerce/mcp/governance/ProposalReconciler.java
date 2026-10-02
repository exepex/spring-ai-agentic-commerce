package io.github.exepex.commerce.mcp.governance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles confirmations that did not finish: the order service did not answer, the order's payment was still pending,
 * or this server stopped half way. Each one places the same order again, which is idempotent. A confirmation only
 * counts as stalled once it is older than {@code commerce.reconciliation.settle-after}, so one still running is left
 * alone.
 */
@Component
public class ProposalReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProposalReconciler.class);

    private final OrderProposalRepository proposals;
    private final ProposalService proposalService;
    private final Duration settleAfter;
    private final Clock clock;

    ProposalReconciler(OrderProposalRepository proposals, ProposalService proposalService,
            @Value("${commerce.reconciliation.settle-after}") Duration settleAfter, Clock clock) {
        this.proposals = proposals;
        this.proposalService = proposalService;
        this.settleAfter = settleAfter;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${commerce.reconciliation.interval}", initialDelayString = "${commerce.reconciliation.interval}")
    public void reconcile() {
        for (OrderProposal proposal : proposals.findByStatusAndConfirmingSinceBefore(OrderProposal.Status.CONFIRMING,
                Instant.now(clock).minus(settleAfter))) {
            try {
                proposalService.settle(proposal);
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not settle the confirmation of proposal {}; it will be retried", proposal.getId(), failure);
            }
        }
    }
}
