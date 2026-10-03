package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.downstream.CatalogApi;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.governance.ProposalService.Proposal;
import io.github.exepex.commerce.mcp.governance.ProposalService.ProposedLine;
import java.math.BigDecimal;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How a proposal is priced from the catalog, shown to the customer, and turned into the order it places. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProposalViews {

    /** A line at the catalog's price now; the order is charged at this price once the customer confirms. */
    static ProposedLine toLine(CatalogApi.Product product, int quantity) {
        return new ProposedLine(product.id(), product.sku(), product.name(), quantity, product.price());
    }

    static BigDecimal totalOf(List<ProposedLine> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static Proposal toView(OrderProposal proposal, List<ProposedLine> lines) {
        return new Proposal(proposal.getId(), proposal.getCustomerEmail(), lines, proposal.getTotal(),
                proposal.getCurrency(), proposal.getStatus(), proposal.getOrderId(), proposal.getFailure(),
                proposal.getCreatedAt());
    }

    static List<OrderApi.RequestedLine> toOrderLines(List<ProposedLine> lines) {
        return lines.stream().map(line -> new OrderApi.RequestedLine(line.productId(), line.quantity())).toList();
    }
}
