package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.GovernanceProperties;
import io.github.exepex.commerce.mcp.cases.CaseService;
import io.github.exepex.commerce.mcp.cases.CaseType;
import io.github.exepex.commerce.mcp.constants.Actors;
import io.github.exepex.commerce.mcp.constants.AuditActions;
import io.github.exepex.commerce.mcp.constants.AuditSummaries;
import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.downstream.Downstream;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.downstream.PaymentApi;
import io.github.exepex.commerce.mcp.downstream.dto.Payment;
import io.github.exepex.commerce.mcp.exception.DecisionNoteTooLongException;
import io.github.exepex.commerce.mcp.exception.DownstreamException;
import io.github.exepex.commerce.mcp.exception.IdempotencyKeyOfAnotherAgentException;
import io.github.exepex.commerce.mcp.exception.IdempotencyKeyReusedException;
import io.github.exepex.commerce.mcp.exception.IdempotencyKeyTooLongException;
import io.github.exepex.commerce.mcp.exception.RefundExceedsRefundableException;
import io.github.exepex.commerce.mcp.exception.RefundReasonTooLongException;
import io.github.exepex.commerce.mcp.exception.RefundRequestNotFailedException;
import io.github.exepex.commerce.mcp.exception.RefundRequestNotFoundException;
import io.github.exepex.commerce.mcp.exception.RefundRequestNotPendingException;
import io.github.exepex.commerce.mcp.governance.dto.NewRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The refund policy, enforced in code rather than in a prompt:
 *
 * <ul>
 *   <li>a refund can never exceed what is still refundable on the order;</li>
 *   <li>refunds that take an order's refunds above the approval threshold wait for a human, so splitting a refund
 *       into smaller ones does not get around it;</li>
 *   <li>every refund carries an idempotency key, so a retried request never pays out twice.</li>
 * </ul>
 */
@Service
public class RefundService {

    /** The longest reason the payment service stores; longer ones are refused before any money moves. */
    private static final int MAX_REASON_LENGTH = 500;

    /** The longest idempotency key and decision note the database stores. */
    private static final int MAX_KEY_LENGTH = 200;
    private static final int MAX_NOTE_LENGTH = 1000;

    private final RefundRequestRepository requests;
    private final PaymentApi payments;
    private final OrderApi orders;
    private final AuditTrail audit;
    private final CaseService cases;
    private final BigDecimal approvalThreshold;
    private final TransactionTemplate transaction;
    private final JdbcClient jdbc;
    private final Clock clock;

    RefundService(RefundRequestRepository requests, PaymentApi payments, OrderApi orders, AuditTrail audit,
            CaseService cases, GovernanceProperties properties, TransactionTemplate transaction,
            JdbcClient jdbc, Clock clock) {
        this.requests = requests;
        this.payments = payments;
        this.orders = orders;
        this.audit = audit;
        this.cases = cases;
        this.approvalThreshold = properties.refundApprovalThreshold();
        this.transaction = transaction;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * The payment is read to refuse a too-large refund early. When the payment service is down the request is still
     * recorded, as failed, so it can be retried with the same key once the service is back; the payment service
     * checks the amount again itself.
     */
    private Payment paymentIfReachable(UUID orderId) {
        try {
            return Downstream.call(DownstreamApis.PAYMENT_SERVICE, () -> payments.getPayment(orderId));
        } catch (DownstreamException failure) {
            if (failure.isRetryable()) {
                return null;
            }
            throw failure;
        }
    }

    /**
     * Asking again with the same idempotency key returns the first request, retrying it only if it had failed.
     * {@code incidentNumber} is the incident an incident run is working, for {@link CaseService#ensureAgentMayPay}.
     */
    public RefundRequest requestRefund(String agentId, UUID orderId, BigDecimal amount, String reason,
            String idempotencyKey, String incidentNumber) {
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new RefundReasonTooLongException(MAX_REASON_LENGTH);
        }
        if (idempotencyKey != null && idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IdempotencyKeyTooLongException(MAX_KEY_LENGTH);
        }
        var earlier = requests.findByIdempotencyKey(idempotencyKey);
        if (earlier.isPresent()) {
            return repeat(earlier.get(), orderId, amount, idempotencyKey, agentId, incidentNumber);
        }
        // An order people are working is theirs: a new refund from an agent could pay out what they are paying back.
        cases.ensureAgentMayPay(orderId, agentId, incidentNumber);
        var payment = paymentIfReachable(orderId);
        if (payment != null && amount.compareTo(payment.refundable()) > 0) {
            throw new RefundExceedsRefundableException(amount, payment.currency(), payment.refundable());
        }
        var currency = payment != null
                ? payment.currency()
                : Downstream.call(DownstreamApis.ORDER_SERVICE, () -> orders.getOrder(orderId)).currency();
        // Saved before the payment service is called. Until it confirms, the request counts as FAILED: if this
        // process stops in between, a retry with the same key is safe and the processor pays out at most once.
        var decided = transaction.execute(status -> {
            // One new refund per order at a time, so two at once cannot each miss the other's amount and stay under
            // the approval limit together.
            AdvisoryLocks.lock(jdbc, orderId.toString(), 0);
            // The same refund may have arrived at the same moment and got the lock first.
            var sameKey = requests.findByIdempotencyKey(idempotencyKey);
            if (sameKey.isPresent()) {
                return new NewRequest(sameKey.get(), null, false);
            }
            var refundedOrAsked = amount.add(requestedBefore(orderId));
            var needsApproval = refundedOrAsked.compareTo(approvalThreshold) > 0;
            return new NewRequest(requests.save(new RefundRequest(orderId, amount, currency, reason, idempotencyKey,
                    agentId, needsApproval ? RefundRequest.Status.PENDING_APPROVAL : RefundRequest.Status.FAILED,
                    Instant.now(clock))), refundedOrAsked, true);
        });
        var request = decided.request();
        if (!decided.isNew()) {
            return repeat(request, orderId, amount, idempotencyKey, agentId, incidentNumber);
        }
        if (request.getStatus() == RefundRequest.Status.PENDING_APPROVAL) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, AuditActions.ISSUE_REFUND,
                    AuditEvent.Outcome.PENDING_APPROVAL, AuditSummaries.REFUND_AWAITS_APPROVAL.formatted(amount,
                            currency, decided.refundedOrAsked(), approvalThreshold), reason);
            return request;
        }
        return execute(request, agentId);
    }

    public RefundRequest approve(UUID requestId, String decidedBy, String note) {
        // Approved requests count as FAILED until the payment service confirms, as when an agent's request runs.
        var request = claimPending(requestId, RefundRequest.Status.FAILED, note);
        request.recordDecision(decidedBy, note, Instant.now(clock));
        audit.record(request.getOrderId(), AuditEvent.ActorType.HUMAN, decidedBy, AuditActions.APPROVE_REFUND,
                AuditEvent.Outcome.SUCCEEDED,
                AuditSummaries.REFUND_APPROVED.formatted(request.getAmount(), request.getCurrency()), note);
        return execute(request, decidedBy);
    }

    public RefundRequest reject(UUID requestId, String decidedBy, String note) {
        var request = claimPending(requestId, RefundRequest.Status.REJECTED, note);
        request.reject(decidedBy, note, Instant.now(clock));
        audit.record(request.getOrderId(), AuditEvent.ActorType.HUMAN, decidedBy, AuditActions.REJECT_REFUND,
                AuditEvent.Outcome.REJECTED,
                AuditSummaries.REFUND_REJECTED.formatted(request.getAmount(), request.getCurrency()), note);
        return requests.save(request);
    }

    /**
     * Runs a failed refund again with its original idempotency key, so it pays out at most once however often it is
     * retried.
     */
    public RefundRequest retry(UUID requestId, String retriedBy) {
        var request = find(requestId);
        if (request.getStatus() != RefundRequest.Status.FAILED) {
            throw new RefundRequestNotFailedException(request.getStatus());
        }
        return execute(request, retriedBy);
    }

    /**
     * The card processor reported a refund failed after accepting it: the customer did not get the money. The request
     * is marked failed, with the processor's reason, and a case opened for the order, together and once per event
     * however often Kafka delivers it. The request is usually executed; it is still failed when this server stopped
     * after the payment service took the refund but before it recorded that, and then the failure is just as real.
     */
    void recordFailedAtProcessor(UUID eventId, UUID orderId, String idempotencyKey, BigDecimal amount, String currency) {
        var failure = AuditSummaries.REFUND_FAILED_AT_PROCESSOR.formatted(amount, currency);
        transaction.executeWithoutResult(status -> {
            if (requests.failAtProcessor(idempotencyKey, failure, Instant.now(clock)) == 0
                    || !cases.isFirstDelivery(eventId, orderId)) {
                return;
            }
            audit.record(orderId, AuditEvent.ActorType.SYSTEM, Actors.PAYMENT_SERVICE, AuditActions.REFUND_FAILED,
                    AuditEvent.Outcome.FAILED, failure, AuditSummaries.IDEMPOTENCY_KEY.formatted(idempotencyKey));
            cases.raise(CaseType.REFUND_FAILED, orderId,
                    AuditSummaries.REFUND_FAILED_CASE.formatted(failure, idempotencyKey), AuditEvent.ActorType.SYSTEM,
                    Actors.PAYMENT_SERVICE);
        });
    }

    public List<RefundRequest> withStatus(RefundRequest.Status status) {
        return requests.findByStatusOrderByCreatedAt(status);
    }

    public List<RefundRequest> recent() {
        return requests.findTop100ByOrderByCreatedAtDesc();
    }

    public List<RefundRequest> forOrder(UUID orderId) {
        return requests.findByOrderIdOrderByCreatedAt(orderId);
    }

    /** A request with a key seen before returns the first request, retrying it only if it had failed. */
    private RefundRequest repeat(RefundRequest earlier, UUID orderId, BigDecimal amount, String idempotencyKey,
            String agentId, String incidentNumber) {
        if (!earlier.matches(orderId, amount)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        // A key is its requester's: another agent repeating it would act, and be audited, as someone it is not.
        if (!earlier.getRequestedBy().equals(agentId)) {
            throw new IdempotencyKeyOfAnotherAgentException(idempotencyKey);
        }
        if (earlier.getStatus() != RefundRequest.Status.FAILED) {
            return earlier;
        }
        // A failed refund of an order people are working is theirs to retry, not the agent's.
        cases.ensureAgentMayPay(orderId, agentId, incidentNumber);
        return execute(earlier, agentId);
    }

    /** What was already refunded or asked for on the order, except refunds a person turned down. */
    private BigDecimal requestedBefore(UUID orderId) {
        return requests.findByOrderIdOrderByCreatedAt(orderId).stream()
                .filter(request -> request.getStatus() != RefundRequest.Status.REJECTED)
                .map(RefundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Takes a pending request out of the approval queue in one statement, so two people deciding at once cannot both
     * act on it: the second one gets a conflict.
     */
    private RefundRequest claimPending(UUID requestId, RefundRequest.Status decided, String note) {
        // Checked before the claim: a note too long to save must not stop the decision after money has moved.
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new DecisionNoteTooLongException(MAX_NOTE_LENGTH);
        }
        var request = find(requestId);
        if (requests.moveStatus(requestId, RefundRequest.Status.PENDING_APPROVAL, decided) == 0) {
            throw new RefundRequestNotPendingException(find(requestId).getStatus());
        }
        return request;
    }

    private RefundRequest execute(RefundRequest request, String actor) {
        var actorType = actor.equals(request.getRequestedBy()) ? AuditEvent.ActorType.AGENT : AuditEvent.ActorType.HUMAN;
        String providerReference = null;
        String failure = null;
        try {
            var refund = Downstream.call(DownstreamApis.PAYMENT_SERVICE, () -> payments.refund(request.getOrderId(),
                    new io.github.exepex.commerce.mcp.downstream.dto.RefundRequest(request.getAmount(),
                            request.getReason(), request.getIdempotencyKey())));
            providerReference = refund.providerReference();
        } catch (DownstreamException unavailable) {
            failure = unavailable.getMessage();
        }
        var outcome = saveOutcome(request, providerReference, failure);
        var amount = AuditSummaries.AMOUNT.formatted(outcome.getAmount(), outcome.getCurrency());
        var keyDetails = AuditSummaries.IDEMPOTENCY_KEY.formatted(outcome.getIdempotencyKey());
        if (providerReference == null) {
            audit.record(outcome.getOrderId(), actorType, actor, AuditActions.ISSUE_REFUND, AuditEvent.Outcome.FAILED,
                    AuditSummaries.REFUND_FAILED.formatted(amount, failure), keyDetails);
        } else if (outcome.getStatus() == RefundRequest.Status.EXECUTED) {
            audit.record(outcome.getOrderId(), actorType, actor, AuditActions.ISSUE_REFUND,
                    AuditEvent.Outcome.SUCCEEDED, AuditSummaries.REFUNDED.formatted(amount, providerReference),
                    outcome.getReason());
        } else {
            audit.record(outcome.getOrderId(), actorType, actor, AuditActions.ISSUE_REFUND, AuditEvent.Outcome.FAILED,
                    AuditSummaries.REFUND_TAKEN_AFTER_FAILURE.formatted(amount), keyDetails);
        }
        return outcome;
    }

    /**
     * Saves what the payment service answered. Another answer about the same refund may have been saved meanwhile,
     * for example by a second retry at the same moment: then a success still wins over a failure, since the money was
     * returned, but nothing undoes a failure the card processor reported. Whatever is stored in the end is returned.
     */
    private RefundRequest saveOutcome(RefundRequest request, String providerReference, String failure) {
        var current = request;
        while (true) {
            if (providerReference != null) {
                current.markExecuted(providerReference, Instant.now(clock));
            } else {
                current.markFailed(failure, Instant.now(clock));
            }
            try {
                return requests.save(current);
            } catch (ObjectOptimisticLockingFailureException changedMeanwhile) {
                current = find(request.getId());
                if (providerReference == null || current.isFailedAtProcessor()
                        || current.getStatus() == RefundRequest.Status.EXECUTED) {
                    return current;
                }
            }
        }
    }

    private RefundRequest find(UUID requestId) {
        return requests.findById(requestId)
                .orElseThrow(() -> new RefundRequestNotFoundException(requestId));
    }
}
