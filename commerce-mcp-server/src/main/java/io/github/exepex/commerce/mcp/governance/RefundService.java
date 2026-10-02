package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.GovernanceProperties;
import io.github.exepex.commerce.mcp.downstream.Downstream;
import io.github.exepex.commerce.mcp.downstream.DownstreamException;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.downstream.PaymentApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private record NewRequest(RefundRequest request, BigDecimal refundedOrAsked, boolean isNew) {}

    /** The longest reason the payment service stores; longer ones are refused before any money moves. */
    static final int MAX_REASON_LENGTH = 500;

    private final RefundRequestRepository requests;
    private final PaymentApi payments;
    private final OrderApi orders;
    private final AuditTrail audit;
    private final BigDecimal approvalThreshold;
    private final TransactionTemplate transaction;
    private final JdbcClient jdbc;
    private final Clock clock;

    RefundService(RefundRequestRepository requests, PaymentApi payments, OrderApi orders, AuditTrail audit,
            GovernanceProperties properties, TransactionTemplate transaction, JdbcClient jdbc, Clock clock) {
        this.requests = requests;
        this.payments = payments;
        this.orders = orders;
        this.audit = audit;
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
    private PaymentApi.Payment paymentIfReachable(UUID orderId) {
        try {
            return Downstream.call("payment service", () -> payments.getPayment(orderId));
        } catch (DownstreamException failure) {
            if (failure.isRetryable()) {
                return null;
            }
            throw failure;
        }
    }

    /** Asking again with the same idempotency key returns the first request, retrying it only if it had failed. */
    public RefundRequest requestRefund(String agentId, UUID orderId, BigDecimal amount, String reason,
            String idempotencyKey) {
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A refund reason can be at most " + MAX_REASON_LENGTH + " characters; say it in one sentence.");
        }
        Optional<RefundRequest> earlier = requests.findByIdempotencyKey(idempotencyKey);
        if (earlier.isPresent()) {
            return repeat(earlier.get(), orderId, amount, idempotencyKey, agentId);
        }
        PaymentApi.Payment payment = paymentIfReachable(orderId);
        if (payment != null && amount.compareTo(payment.refundable()) > 0) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "A refund of " + amount + " "
                    + payment.currency() + " exceeds the " + payment.refundable() + " still refundable on this order.");
        }
        String currency = payment != null
                ? payment.currency()
                : Downstream.call("order service", () -> orders.getOrder(orderId)).currency();
        // Saved before the payment service is called. Until it confirms, the request counts as FAILED: if this
        // process stops in between, a retry with the same key is safe and the processor pays out at most once.
        NewRequest decided = transaction.execute(status -> {
            // One new refund per order at a time, so two at once cannot each miss the other's amount and stay under
            // the approval limit together.
            jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:orderId, 0))")
                    .param("orderId", orderId.toString())
                    .query((row, number) -> number)
                    .single();
            // The same refund may have arrived at the same moment and got the lock first.
            Optional<RefundRequest> sameKey = requests.findByIdempotencyKey(idempotencyKey);
            if (sameKey.isPresent()) {
                return new NewRequest(sameKey.get(), null, false);
            }
            BigDecimal refundedOrAsked = amount.add(requestedBefore(orderId));
            boolean needsApproval = refundedOrAsked.compareTo(approvalThreshold) > 0;
            return new NewRequest(requests.save(new RefundRequest(orderId, amount, currency, reason, idempotencyKey,
                    agentId, needsApproval ? RefundRequest.Status.PENDING_APPROVAL : RefundRequest.Status.FAILED,
                    Instant.now(clock))), refundedOrAsked, true);
        });
        RefundRequest request = decided.request();
        if (!decided.isNew()) {
            return repeat(request, orderId, amount, idempotencyKey, agentId);
        }
        if (request.getStatus() == RefundRequest.Status.PENDING_APPROVAL) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, "issue_refund", AuditEvent.Outcome.PENDING_APPROVAL,
                    "Refund of " + amount + " " + currency + " takes this order's refunds to " + decided.refundedOrAsked()
                            + ", above the " + approvalThreshold + " limit, and waits for a human to approve it",
                    reason);
            return request;
        }
        return execute(request, agentId);
    }

    public RefundRequest approve(UUID requestId, String decidedBy, String note) {
        // Approved requests count as FAILED until the payment service confirms, as when an agent's request runs.
        RefundRequest request = claimPending(requestId, RefundRequest.Status.FAILED);
        request.recordDecision(decidedBy, note, Instant.now(clock));
        audit.record(request.getOrderId(), AuditEvent.ActorType.HUMAN, decidedBy, "approve_refund",
                AuditEvent.Outcome.SUCCEEDED, "Approved a refund of " + request.getAmount() + " " + request.getCurrency(), note);
        return execute(request, decidedBy);
    }

    public RefundRequest reject(UUID requestId, String decidedBy, String note) {
        RefundRequest request = claimPending(requestId, RefundRequest.Status.REJECTED);
        request.reject(decidedBy, note, Instant.now(clock));
        audit.record(request.getOrderId(), AuditEvent.ActorType.HUMAN, decidedBy, "reject_refund",
                AuditEvent.Outcome.REJECTED, "Rejected a refund of " + request.getAmount() + " " + request.getCurrency(), note);
        return requests.save(request);
    }

    /** Runs a failed refund again with its original idempotency key. */
    public RefundRequest retry(UUID requestId, String retriedBy) {
        RefundRequest request = find(requestId);
        if (request.getStatus() != RefundRequest.Status.FAILED) {
            throw new GovernanceException(HttpStatus.CONFLICT, "Refund request is " + request.getStatus() + ", not failed");
        }
        return execute(request, retriedBy);
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
            String agentId) {
        if (!earlier.matches(orderId, amount)) {
            throw new GovernanceException(HttpStatus.CONFLICT, "Idempotency key " + idempotencyKey
                    + " was already used for a different refund. Use a new key for a new refund.");
        }
        return earlier.getStatus() == RefundRequest.Status.FAILED ? execute(earlier, agentId) : earlier;
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
    private RefundRequest claimPending(UUID requestId, RefundRequest.Status decided) {
        RefundRequest request = find(requestId);
        if (requests.moveStatus(requestId, RefundRequest.Status.PENDING_APPROVAL, decided) == 0) {
            throw new GovernanceException(HttpStatus.CONFLICT, "Refund request is " + find(requestId).getStatus()
                    + ", not pending approval");
        }
        return request;
    }

    private RefundRequest execute(RefundRequest request, String actor) {
        AuditEvent.ActorType actorType = actor.equals(request.getRequestedBy()) ? AuditEvent.ActorType.AGENT : AuditEvent.ActorType.HUMAN;
        try {
            PaymentApi.Refund refund = Downstream.call("payment service", () -> payments.refund(request.getOrderId(),
                    new PaymentApi.RefundRequest(request.getAmount(), request.getReason(), request.getIdempotencyKey())));
            request.markExecuted(refund.providerReference(), Instant.now(clock));
            audit.record(request.getOrderId(), actorType, actor, "issue_refund", AuditEvent.Outcome.SUCCEEDED,
                    "Refunded " + request.getAmount() + " " + request.getCurrency() + " (" + refund.providerReference() + ")",
                    request.getReason());
        } catch (DownstreamException failure) {
            request.markFailed(failure.getMessage(), Instant.now(clock));
            audit.record(request.getOrderId(), actorType, actor, "issue_refund", AuditEvent.Outcome.FAILED,
                    "Refund of " + request.getAmount() + " " + request.getCurrency() + " failed: " + failure.getMessage(),
                    "Idempotency key " + request.getIdempotencyKey());
        }
        return requests.save(request);
    }

    private RefundRequest find(UUID requestId) {
        return requests.findById(requestId)
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND, "Refund request " + requestId + " does not exist"));
    }
}
