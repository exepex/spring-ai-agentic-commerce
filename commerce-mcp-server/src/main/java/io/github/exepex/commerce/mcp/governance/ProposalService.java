package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.downstream.CatalogApi;
import io.github.exepex.commerce.mcp.downstream.Downstream;
import io.github.exepex.commerce.mcp.downstream.DownstreamException;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Agents propose orders; customers confirm them. Only a confirmation places the order and charges the card. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalService {

    public record RequestedLine(String productId, int quantity) {}

    public record ProposedLine(UUID productId, String sku, String name, int quantity, BigDecimal unitPrice) {}

    public record Proposal(UUID id, String customerEmail, List<ProposedLine> lines, BigDecimal total, String currency,
            OrderProposal.Status status, UUID orderId, String failure, Instant createdAt) {}

    /** An order in these is still being placed or paid; in any other it was placed and paid first. */
    private static final Set<String> UNSETTLED_ORDER_STATUSES = Set.of("PLACED", "PAYMENT_PENDING");

    private final OrderProposalRepository proposals;
    private final CatalogApi catalog;
    private final OrderApi orders;
    private final AuditTrail audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** Prices the lines from the catalog and checks stock now; stock is only reserved once the customer confirms. */
    public Proposal propose(String customerEmail, List<RequestedLine> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "An order needs at least one line");
        }
        if (requestedLines.stream().map(RequestedLine::productId).distinct().count() < requestedLines.size()) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Each product can appear on only one line; put the whole quantity on that line.");
        }
        Map<UUID, CatalogApi.Product> productsById = Downstream.call("catalog", catalog::listProducts).stream()
                .collect(Collectors.toMap(CatalogApi.Product::id, Function.identity()));
        List<ProposedLine> lines = requestedLines.stream().map(requested -> {
            CatalogApi.Product product = productsById.get(parseProductId(requested.productId()));
            if (product == null) {
                throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Product " + requested.productId() + " does not exist. Use search_products to find product ids.");
            }
            if (requested.quantity() < 1 || requested.quantity() > product.available()) {
                throw new GovernanceException(HttpStatus.CONFLICT, "Cannot order " + requested.quantity() + " of "
                        + product.name() + ": " + product.available() + " available.");
            }
            return ProposalViews.toLine(product, requested.quantity());
        }).toList();
        BigDecimal total = ProposalViews.totalOf(lines);
        String currency = productsById.get(lines.getFirst().productId()).currency();
        OrderProposal proposal = proposals.save(new OrderProposal(customerEmail, jsonMapper.writeValueAsString(lines),
                total, currency, Instant.now(clock)));
        return view(proposal);
    }

    /**
     * The customer's confirmation: places the order, which reserves the stock and charges the card. If the order is
     * not settled yet, because its payment is pending or the order service did not answer, the proposal stays
     * {@code CONFIRMING} and {@link ProposalReconciler} settles it later.
     */
    public Proposal confirm(UUID proposalId, String paymentMethod) {
        get(proposalId);
        // Claimed in one statement, so a double click or a retried request cannot place and charge the order twice.
        if (proposals.claimForConfirmation(proposalId, paymentMethod, Instant.now(clock)) == 0) {
            return get(proposalId);
        }
        settle(proposals.findById(proposalId).orElseThrow());
        return get(proposalId);
    }

    /**
     * Places the confirmed proposal's order, or asks for it again: the order's id is the proposal's id, so the order
     * service returns the order it placed before instead of placing a second one. Only the request that ends the
     * confirmation records it in the audit trail.
     */
    void settle(OrderProposal proposal) {
        List<OrderApi.RequestedLine> lines = ProposalViews.toOrderLines(linesOf(proposal));
        try {
            OrderApi.Order order = Downstream.call("order service", () -> orders.placeOrder(new OrderApi.PlaceOrderRequest(
                    proposal.getId(), proposal.getCustomerEmail(), lines, proposal.getPaymentMethod())));
            if (UNSETTLED_ORDER_STATUSES.contains(order.status())) {
                proposals.linkPendingOrder(proposal.getId(), order.id());
            } else if (proposals.settle(proposal.getId(), OrderProposal.Status.CONFIRMED, order.id(), null) == 1) {
                audit.record(order.id(), AuditEvent.ActorType.HUMAN, proposal.getCustomerEmail(), "confirm_order",
                        AuditEvent.Outcome.SUCCEEDED, "Customer confirmed the proposed order and paid " + order.total()
                                + " " + order.currency(),
                        "Proposal " + proposal.getId());
            }
        } catch (DownstreamException failure) {
            if (failure.isRetryable()) {
                log.warn("The order for proposal {} is not settled yet; it will be asked for again", proposal.getId());
            } else if (proposals.settle(proposal.getId(), OrderProposal.Status.FAILED, null, failure.getMessage()) == 1) {
                audit.record(failedOrderId(failure), AuditEvent.ActorType.HUMAN, proposal.getCustomerEmail(),
                        "confirm_order", AuditEvent.Outcome.FAILED, "Order could not be placed: " + failure.getMessage(),
                        "Proposal " + proposal.getId());
            }
        }
    }

    public Proposal get(UUID proposalId) {
        return view(proposals.findById(proposalId)
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND, "Order proposal " + proposalId + " does not exist")));
    }

    private Proposal view(OrderProposal proposal) {
        return ProposalViews.toView(proposal, linesOf(proposal));
    }

    private List<ProposedLine> linesOf(OrderProposal proposal) {
        return jsonMapper.readValue(proposal.getLines(), new TypeReference<List<ProposedLine>>() {});
    }

    /** The order service keeps an order whose payment failed and names it, so the failure shows on its timeline. */
    private static UUID failedOrderId(DownstreamException failure) {
        Map<String, Object> properties = failure.getBody().getProperties();
        Object orderId = properties == null ? null : properties.get("orderId");
        return orderId == null ? null : UUID.fromString(orderId.toString());
    }

    private static UUID parseProductId(String productId) {
        try {
            return UUID.fromString(productId);
        } catch (IllegalArgumentException notAUuid) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "'" + productId + "' is not a product id. Use search_products to find product ids.");
        }
    }
}
