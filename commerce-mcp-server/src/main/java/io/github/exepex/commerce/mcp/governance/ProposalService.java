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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Agents propose orders; customers confirm them. Only a confirmation places the order and charges the card. */
@Service
public class ProposalService {

    public record RequestedLine(String productId, int quantity) {}

    public record ProposedLine(UUID productId, String sku, String name, int quantity, BigDecimal unitPrice) {}

    public record Proposal(UUID id, String customerEmail, List<ProposedLine> lines, BigDecimal total, String currency,
            OrderProposal.Status status, UUID orderId, String failure, Instant createdAt) {}

    private final OrderProposalRepository proposals;
    private final CatalogApi catalog;
    private final OrderApi orders;
    private final AuditTrail audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    ProposalService(OrderProposalRepository proposals, CatalogApi catalog, OrderApi orders, AuditTrail audit,
            JsonMapper jsonMapper, Clock clock) {
        this.proposals = proposals;
        this.catalog = catalog;
        this.orders = orders;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Prices the lines from the catalog and checks stock now; stock is only reserved once the customer confirms. */
    public Proposal propose(String customerEmail, List<RequestedLine> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "An order needs at least one line");
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
            return new ProposedLine(product.id(), product.sku(), product.name(), requested.quantity(), product.price());
        }).toList();
        BigDecimal total = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = productsById.get(lines.getFirst().productId()).currency();
        OrderProposal proposal = proposals.save(new OrderProposal(customerEmail, jsonMapper.writeValueAsString(lines),
                total, currency, Instant.now(clock)));
        return view(proposal);
    }

    /** The customer's confirmation: places the order, which reserves the stock and charges the card. */
    public Proposal confirm(UUID proposalId, String paymentMethod) {
        OrderProposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND, "Order proposal " + proposalId + " does not exist"));
        // Claimed in one statement, so a double click or a retried request cannot place and charge the order twice.
        if (proposals.moveStatus(proposalId, OrderProposal.Status.PROPOSED, OrderProposal.Status.CONFIRMING) == 0) {
            return get(proposalId);
        }
        List<OrderApi.RequestedLine> lines = linesOf(proposal).stream()
                .map(line -> new OrderApi.RequestedLine(line.productId(), line.quantity()))
                .toList();
        try {
            OrderApi.Order order = Downstream.call("order service", () -> orders.placeOrder(
                    new OrderApi.PlaceOrderRequest(proposal.getCustomerEmail(), lines, paymentMethod)));
            proposal.markConfirmed(order.id());
            audit.record(order.id(), AuditEvent.ActorType.HUMAN, proposal.getCustomerEmail(), "confirm_order",
                    AuditEvent.Outcome.SUCCEEDED, "Customer confirmed the proposed order and paid " + order.total() + " "
                            + order.currency(),
                    "Proposal " + proposal.getId());
        } catch (DownstreamException failure) {
            proposal.markFailed(failure.getMessage());
            audit.record(failedOrderId(failure), AuditEvent.ActorType.HUMAN, proposal.getCustomerEmail(), "confirm_order",
                    AuditEvent.Outcome.FAILED, "Order could not be placed: " + failure.getMessage(),
                    "Proposal " + proposal.getId());
        }
        return view(proposals.save(proposal));
    }

    public Proposal get(UUID proposalId) {
        return view(proposals.findById(proposalId)
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND, "Order proposal " + proposalId + " does not exist")));
    }

    private Proposal view(OrderProposal proposal) {
        return new Proposal(proposal.getId(), proposal.getCustomerEmail(), linesOf(proposal), proposal.getTotal(),
                proposal.getCurrency(), proposal.getStatus(), proposal.getOrderId(), proposal.getFailure(),
                proposal.getCreatedAt());
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
