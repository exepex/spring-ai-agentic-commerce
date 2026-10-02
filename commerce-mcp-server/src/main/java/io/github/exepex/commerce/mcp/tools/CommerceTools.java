package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.downstream.CatalogApi;
import io.github.exepex.commerce.mcp.downstream.Downstream;
import io.github.exepex.commerce.mcp.downstream.DownstreamException;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.downstream.PaymentApi;
import io.github.exepex.commerce.mcp.downstream.ShippingApi;
import io.github.exepex.commerce.mcp.governance.EscalationService;
import io.github.exepex.commerce.mcp.governance.NotificationService;
import io.github.exepex.commerce.mcp.governance.ProposalService;
import io.github.exepex.commerce.mcp.governance.RefundRequest;
import io.github.exepex.commerce.mcp.governance.RefundService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The shop's MCP tools. Each call goes through {@link ToolGuard}: the calling agent must be permitted to use the tool,
 * customer-facing agents only see their own customer's orders, and every call is recorded in the audit trail.
 */
@Component
class CommerceTools {

    private static final String CUSTOMER_EMAIL = "The customer's email. In a customer conversation the application fills it in.";

    record ProductSummary(UUID productId, String sku, String name, String description, BigDecimal price,
            String currency, int available) {}

    record OrderSummary(UUID orderId, String status, BigDecimal total, String currency, Instant createdAt, String items) {}

    /** {@code message} is set only when the payment could not be read, and says so: a missing payment is not unpaid. */
    record PaymentSummary(String status, BigDecimal paid, BigDecimal refunded, BigDecimal refundable, String currency,
            String message) {}

    record ShipmentSummary(String trackingNumber, String status, LocalDate estimatedDelivery) {}

    record RefundSummary(UUID refundRequestId, BigDecimal amount, String status, String reason) {}

    record OrderDetails(UUID orderId, String customerEmail, String status, BigDecimal total, String currency,
            Instant createdAt, String cancellationReason, List<OrderApi.OrderLine> lines, PaymentSummary payment,
            ShipmentSummary shipment, List<RefundSummary> refunds) {}

    record RefundResult(UUID refundRequestId, String status, BigDecimal amount, String currency, String message) {}

    record Acknowledgement(UUID id, String message) {}

    private final ToolGuard guard;
    private final CatalogApi catalog;
    private final OrderApi orders;
    private final PaymentApi payments;
    private final ShippingApi shipping;
    private final ProposalService proposals;
    private final RefundService refunds;
    private final NotificationService notifications;
    private final EscalationService escalations;

    CommerceTools(ToolGuard guard, CatalogApi catalog, OrderApi orders, PaymentApi payments, ShippingApi shipping,
            ProposalService proposals, RefundService refunds, NotificationService notifications,
            EscalationService escalations) {
        this.guard = guard;
        this.catalog = catalog;
        this.orders = orders;
        this.payments = payments;
        this.shipping = shipping;
        this.proposals = proposals;
        this.refunds = refunds;
        this.notifications = notifications;
        this.escalations = escalations;
    }

    @McpTool(name = "search_products", description = """
            Search the catalog. Returns matching products with their id, price and how many are available. \
            Leave the query empty to list every product.""")
    List<ProductSummary> searchProducts(McpTransportContext context,
            @McpToolParam(description = "Words to look for in the product name, SKU or description", required = false) String query) {
        return guard.run(context, "search_products", null, "Searched products for '" + query + "'", false, agentId -> {
            List<String> words = query == null || query.isBlank()
                    ? List.of()
                    : Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+")).toList();
            return Downstream.call("catalog", catalog::listProducts).stream()
                    .filter(product -> words.stream().allMatch(word -> (product.name() + " " + product.sku() + " "
                            + product.description()).toLowerCase(Locale.ROOT).contains(word)))
                    .map(product -> new ProductSummary(product.id(), product.sku(), product.name(), product.description(),
                            product.price(), product.currency(), product.available()))
                    .toList();
        });
    }

    @McpTool(name = "find_customer_orders", description = "List a customer's orders, newest first.")
    List<OrderSummary> findCustomerOrders(McpTransportContext context,
            @McpToolParam(description = CUSTOMER_EMAIL) String customerEmail) {
        return guard.run(context, "find_customer_orders", null, "Listed the orders of " + customerEmail, false, agentId ->
                Downstream.call("order service", () -> orders.findOrders(ToolGuard.requireCustomer(customerEmail))).stream()
                        .map(order -> new OrderSummary(order.id(), order.status(), order.total(), order.currency(),
                                order.createdAt(), order.lines().stream()
                                        .map(line -> line.quantity() + " x " + line.productName())
                                        .collect(Collectors.joining(", "))))
                        .toList());
    }

    @McpTool(name = "get_order", description = """
            Get everything about one order: its lines and status, the payment (paid, refunded and still refundable), \
            the shipment, and any refunds already requested for it.""")
    OrderDetails getOrder(McpTransportContext context,
            @McpToolParam(description = "The order id") String orderId,
            @McpToolParam(description = CUSTOMER_EMAIL, required = false) String customerEmail) {
        UUID id = ToolGuard.parseOrderId(orderId);
        return guard.run(context, "get_order", id, "Looked up the order", false, agentId -> {
            OrderApi.Order order = Downstream.call("order service", () -> orders.getOrder(id));
            guard.ensureCustomerOwns(agentId, order, customerEmail);
            return new OrderDetails(order.id(), order.customerEmail(), order.status(), order.total(), order.currency(),
                    order.createdAt(), order.cancellationReason(), order.lines(), paymentOf(id), shipmentOf(id),
                    refunds.forOrder(id).stream()
                            .map(refund -> new RefundSummary(refund.getId(), refund.getAmount(), refund.getStatus().name(),
                                    refund.getReason()))
                            .toList());
        });
    }

    @McpTool(name = "track_shipment", description = "Get the tracking number, status and estimated delivery date of an order's shipment.")
    ShipmentSummary trackShipment(McpTransportContext context,
            @McpToolParam(description = "The order id") String orderId,
            @McpToolParam(description = CUSTOMER_EMAIL, required = false) String customerEmail) {
        UUID id = ToolGuard.parseOrderId(orderId);
        return guard.run(context, "track_shipment", id, "Tracked the shipment", false, agentId -> {
            guard.ensureCustomerOwns(agentId, Downstream.call("order service", () -> orders.getOrder(id)), customerEmail);
            ShippingApi.Shipment shipment = Downstream.call("shipping service", () -> shipping.getShipment(id));
            return new ShipmentSummary(shipment.trackingNumber(), shipment.status(), shipment.estimatedDelivery());
        });
    }

    @McpTool(name = "propose_order", description = """
            Put together an order for the customer to confirm. Nothing is charged or reserved: the customer sees the \
            proposal in the chat and must press "Confirm and pay" themselves. Use product ids from search_products.""")
    ProposalService.Proposal proposeOrder(McpTransportContext context,
            @McpToolParam(description = CUSTOMER_EMAIL) String customerEmail,
            @McpToolParam(description = "The products and quantities to order") List<ProposalService.RequestedLine> lines) {
        return guard.run(context, "propose_order", null, "Proposed an order to " + customerEmail, false,
                agentId -> proposals.propose(ToolGuard.requireCustomer(customerEmail), lines));
    }

    @McpTool(name = "cancel_order", description = """
            Cancel an order: its stock goes back to the shelf and its shipment is cancelled. Cancelling does not \
            refund the payment; use issue_refund for that. Cancelling an already cancelled order changes nothing.""")
    OrderSummary cancelOrder(McpTransportContext context,
            @McpToolParam(description = "The order id") String orderId,
            @McpToolParam(description = "Why the order is cancelled, in a sentence") String reason,
            @McpToolParam(description = CUSTOMER_EMAIL, required = false) String customerEmail) {
        UUID id = ToolGuard.parseOrderId(orderId);
        return guard.run(context, "cancel_order", id, "Cancelled the order: " + reason, false, agentId -> {
            guard.ensureCustomerOwns(agentId, Downstream.call("order service", () -> orders.getOrder(id)), customerEmail);
            OrderApi.Order order = Downstream.call("order service",
                    () -> orders.cancelOrder(id, new OrderApi.CancelOrderRequest(reason)));
            return new OrderSummary(order.id(), order.status(), order.total(), order.currency(), order.createdAt(), null);
        });
    }

    @McpTool(name = "issue_refund", description = """
            Refund part or all of an order's payment. Refunds above the approval limit are not paid out straight away: \
            they wait for a human, and the result says so. Choose an idempotency key for each new refund, for example \
            "refund-<order id>-1". If a refund fails because a service is down, retrying with the SAME key is safe and \
            never pays out twice.""")
    RefundResult issueRefund(McpTransportContext context,
            @McpToolParam(description = "The order id") String orderId,
            @McpToolParam(description = "The amount to refund, in the order's currency") BigDecimal amount,
            @McpToolParam(description = "Why the customer is refunded, in a sentence") String reason,
            @McpToolParam(description = "A key that identifies this refund; reuse it only to retry the same refund") String idempotencyKey,
            @McpToolParam(description = CUSTOMER_EMAIL, required = false) String customerEmail) {
        UUID id = ToolGuard.parseOrderId(orderId);
        return guard.run(context, "issue_refund", id, "Asked to refund " + amount, true, agentId -> {
            guard.ensureCustomerOwns(agentId, Downstream.call("order service", () -> orders.getOrder(id)), customerEmail);
            RefundRequest request = refunds.requestRefund(agentId, id, amount, reason, idempotencyKey);
            return new RefundResult(request.getId(), request.getStatus().name(), request.getAmount(),
                    request.getCurrency(), messageFor(request));
        });
    }

    @McpTool(name = "notify_customer", description = "Send the customer of an order a short message, for example to explain a cancellation and refund.")
    Acknowledgement notifyCustomer(McpTransportContext context,
            @McpToolParam(description = "The order id") String orderId,
            @McpToolParam(description = "The message, written to the customer") String message) {
        UUID id = ToolGuard.parseOrderId(orderId);
        return guard.run(context, "notify_customer", id, "Notified the customer", true, agentId -> {
            OrderApi.Order order = Downstream.call("order service", () -> orders.getOrder(id));
            return new Acknowledgement(notifications.notifyCustomer(agentId, id, order.customerEmail(), message).getId(),
                    "The customer was notified");
        });
    }

    @McpTool(name = "escalate_to_human", description = """
            Hand a problem to the operations team when you cannot or should not resolve it yourself, for example \
            when a service keeps failing. Say what happened, what you already did, and what you recommend.""")
    Acknowledgement escalateToHuman(McpTransportContext context,
            @McpToolParam(description = "The order id, if the problem is about one order", required = false) String orderId,
            @McpToolParam(description = "What happened, what you already did, and what you recommend") String summary) {
        UUID id = orderId == null || orderId.isBlank() ? null : ToolGuard.parseOrderId(orderId);
        return guard.run(context, "escalate_to_human", id, "Escalated to a human", true, agentId ->
                new Acknowledgement(escalations.escalate(agentId, id, summary).getId(),
                        "The operations team has the escalation and will take it from here"));
    }

    private PaymentSummary paymentOf(UUID orderId) {
        try {
            PaymentApi.Payment payment = Downstream.call("payment service", () -> payments.getPayment(orderId));
            return new PaymentSummary(payment.status(), payment.amount(), payment.refundedAmount(), payment.refundable(),
                    payment.currency(), null);
        } catch (DownstreamException failure) {
            return failure.isRetryable()
                    ? new PaymentSummary("UNKNOWN", null, null, null, null, failure.getMessage())
                    : null;
        }
    }

    private ShipmentSummary shipmentOf(UUID orderId) {
        try {
            ShippingApi.Shipment shipment = Downstream.call("shipping service", () -> shipping.getShipment(orderId));
            return new ShipmentSummary(shipment.trackingNumber(), shipment.status(), shipment.estimatedDelivery());
        } catch (DownstreamException unavailable) {
            return null;
        }
    }

    private static String messageFor(RefundRequest request) {
        return switch (request.getStatus()) {
            case EXECUTED -> "Refunded " + request.getAmount() + " " + request.getCurrency() + ".";
            case PENDING_APPROVAL -> "This refund is above the approval limit and is waiting for a human to approve it. "
                    + "Do not retry it. Tell the customer it is being reviewed.";
            case FAILED -> "The refund did not go through: " + request.getFailure() + " Retrying with the same "
                    + "idempotency key is safe. If it keeps failing, escalate to a human.";
            case REJECTED -> "A human rejected this refund: " + request.getDecisionNote();
        };
    }
}
