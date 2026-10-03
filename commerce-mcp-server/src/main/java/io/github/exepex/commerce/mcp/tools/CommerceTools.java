package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.cases.CaseService;
import io.github.exepex.commerce.mcp.cases.CaseType;
import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.constants.ToolDescriptions;
import io.github.exepex.commerce.mcp.constants.ToolMessages;
import io.github.exepex.commerce.mcp.constants.ToolNames;
import io.github.exepex.commerce.mcp.downstream.CatalogApi;
import io.github.exepex.commerce.mcp.downstream.Downstream;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.downstream.PaymentApi;
import io.github.exepex.commerce.mcp.downstream.ShippingApi;
import io.github.exepex.commerce.mcp.downstream.dto.CancelOrderRequest;
import io.github.exepex.commerce.mcp.downstream.dto.Order;
import io.github.exepex.commerce.mcp.exception.DownstreamException;
import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.NotificationService;
import io.github.exepex.commerce.mcp.governance.ProposalService;
import io.github.exepex.commerce.mcp.governance.RefundService;
import io.github.exepex.commerce.mcp.governance.dto.Proposal;
import io.github.exepex.commerce.mcp.governance.dto.RequestedLine;
import io.github.exepex.commerce.mcp.tools.dto.Acknowledgement;
import io.github.exepex.commerce.mcp.tools.dto.OrderDetails;
import io.github.exepex.commerce.mcp.tools.dto.OrderSummary;
import io.github.exepex.commerce.mcp.tools.dto.PaymentSummary;
import io.github.exepex.commerce.mcp.tools.dto.ProductSummary;
import io.github.exepex.commerce.mcp.tools.dto.RefundResult;
import io.github.exepex.commerce.mcp.tools.dto.ShipmentSummary;
import io.modelcontextprotocol.common.McpTransportContext;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The shop's MCP tools. Each call goes through {@link ToolGuard}: the calling agent must be permitted to use the tool,
 * customer-facing agents only see their own customer's orders, and every call is recorded in the audit trail.
 */
@Component
@RequiredArgsConstructor
class CommerceTools {

    private final ToolGuard guard;
    private final CatalogApi catalog;
    private final OrderApi orders;
    private final PaymentApi payments;
    private final ShippingApi shipping;
    private final ProposalService proposals;
    private final RefundService refunds;
    private final NotificationService notifications;
    private final CaseService cases;

    @McpTool(name = ToolNames.SEARCH_PRODUCTS, description = ToolDescriptions.SEARCH_PRODUCTS)
    List<ProductSummary> searchProducts(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.PRODUCT_QUERY, required = false) String query) {
        return guard.run(context, ToolNames.SEARCH_PRODUCTS, null, ToolMessages.SEARCHED_PRODUCTS.formatted(query),
                false, agentId -> {
                    var words = query == null || query.isBlank()
                            ? List.<String>of()
                            : Arrays.stream(query.toLowerCase(Locale.ROOT).split(ToolMessages.QUERY_WORD_SEPARATOR))
                                    .toList();
                    return Downstream.call(DownstreamApis.CATALOG, catalog::listProducts).stream()
                            .filter(product -> words.stream().allMatch(word -> ToolMessages.SEARCHABLE_PRODUCT_TEXT
                                    .formatted(product.name(), product.sku(), product.description())
                                    .toLowerCase(Locale.ROOT).contains(word)))
                            .map(ToolMapper::toSummary)
                            .toList();
                });
    }

    @McpTool(name = ToolNames.FIND_CUSTOMER_ORDERS, description = ToolDescriptions.FIND_CUSTOMER_ORDERS)
    List<OrderSummary> findCustomerOrders(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.CUSTOMER_EMAIL) String customerEmail) {
        return guard.run(context, ToolNames.FIND_CUSTOMER_ORDERS, null,
                ToolMessages.LISTED_ORDERS.formatted(customerEmail), false, agentId ->
                        Downstream.call(DownstreamApis.ORDER_SERVICE,
                                        () -> orders.findOrders(ToolArguments.requireCustomer(customerEmail)))
                                .stream()
                                .map(order -> ToolMapper.toSummary(order, ToolMapper.itemsOf(order)))
                                .toList());
    }

    @McpTool(name = ToolNames.GET_ORDER, description = ToolDescriptions.GET_ORDER)
    OrderDetails getOrder(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.ORDER_ID) String orderId,
            @McpToolParam(description = ToolDescriptions.CUSTOMER_EMAIL, required = false) String customerEmail) {
        var id = ToolArguments.parseOrderId(orderId);
        return guard.run(context, ToolNames.GET_ORDER, id, ToolMessages.LOOKED_UP_ORDER, false, agentId -> {
            var order = Downstream.call(DownstreamApis.ORDER_SERVICE, () -> orders.getOrder(id));
            guard.ensureCustomerOwns(agentId, order, customerEmail);
            return ToolMapper.toDetails(order, paymentOf(id), shipmentOf(id), refunds.forOrder(id),
                    notifications.forOrder(id));
        });
    }

    @McpTool(name = ToolNames.TRACK_SHIPMENT, description = ToolDescriptions.TRACK_SHIPMENT)
    ShipmentSummary trackShipment(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.ORDER_ID) String orderId,
            @McpToolParam(description = ToolDescriptions.CUSTOMER_EMAIL, required = false) String customerEmail) {
        var id = ToolArguments.parseOrderId(orderId);
        return guard.run(context, ToolNames.TRACK_SHIPMENT, id, ToolMessages.TRACKED_SHIPMENT, false, agentId -> {
            guard.ensureCustomerOwns(agentId, orderOf(id), customerEmail);
            return ToolMapper.toSummary(Downstream.call(DownstreamApis.SHIPPING_SERVICE, () -> shipping.getShipment(id)));
        });
    }

    @McpTool(name = ToolNames.PROPOSE_ORDER, description = ToolDescriptions.PROPOSE_ORDER)
    Proposal proposeOrder(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.CUSTOMER_EMAIL) String customerEmail,
            @McpToolParam(description = ToolDescriptions.PROPOSED_LINES) List<RequestedLine> lines) {
        return guard.run(context, ToolNames.PROPOSE_ORDER, null, ToolMessages.PROPOSED_ORDER.formatted(customerEmail),
                false, agentId -> proposals.propose(ToolArguments.requireCustomer(customerEmail), lines));
    }

    @McpTool(name = ToolNames.CANCEL_ORDER, description = ToolDescriptions.CANCEL_ORDER)
    OrderSummary cancelOrder(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.ORDER_ID) String orderId,
            @McpToolParam(description = ToolDescriptions.CANCELLATION_REASON) String reason,
            @McpToolParam(description = ToolDescriptions.CUSTOMER_EMAIL, required = false) String customerEmail) {
        var id = ToolArguments.parseOrderId(orderId);
        return guard.run(context, ToolNames.CANCEL_ORDER, id, ToolMessages.CANCELLED_ORDER.formatted(reason), false,
                agentId -> {
                    guard.ensureCustomerOwns(agentId, orderOf(id), customerEmail);
                    var order = Downstream.call(DownstreamApis.ORDER_SERVICE,
                            () -> orders.cancelOrder(id, new CancelOrderRequest(reason)));
                    return ToolMapper.toSummary(order, null);
                });
    }

    @McpTool(name = ToolNames.ISSUE_REFUND, description = ToolDescriptions.ISSUE_REFUND)
    RefundResult issueRefund(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.ORDER_ID) String orderId,
            @McpToolParam(description = ToolDescriptions.REFUND_AMOUNT) BigDecimal amount,
            @McpToolParam(description = ToolDescriptions.REFUND_REASON) String reason,
            @McpToolParam(description = ToolDescriptions.REFUND_KEY) String idempotencyKey,
            @McpToolParam(description = ToolDescriptions.CUSTOMER_EMAIL, required = false) String customerEmail,
            @McpToolParam(description = ToolDescriptions.INCIDENT_NUMBER, required = false) String incidentNumber) {
        var id = ToolArguments.parseOrderId(orderId);
        return guard.run(context, ToolNames.ISSUE_REFUND, id, ToolMessages.ASKED_TO_REFUND.formatted(amount), true,
                agentId -> {
                    guard.ensureCustomerOwns(agentId, orderOf(id), customerEmail);
                    var request = refunds.requestRefund(agentId, id, amount, reason, idempotencyKey, incidentNumber);
                    return ToolMapper.toResult(request);
                });
    }

    @McpTool(name = ToolNames.NOTIFY_CUSTOMER, description = ToolDescriptions.NOTIFY_CUSTOMER)
    Acknowledgement notifyCustomer(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.ORDER_ID) String orderId,
            @McpToolParam(description = ToolDescriptions.MESSAGE) String message,
            @McpToolParam(description = ToolDescriptions.MESSAGE_KEY, required = false) String idempotencyKey) {
        var id = ToolArguments.parseOrderId(orderId);
        return guard.run(context, ToolNames.NOTIFY_CUSTOMER, id, ToolMessages.NOTIFIED_CUSTOMER, true, agentId -> {
            var order = orderOf(id);
            var sent = notifications.notifyCustomer(agentId, id, order.customerEmail(), message,
                    ToolArguments.optionalKey(idempotencyKey));
            return new Acknowledgement(sent.notification().getId(), sent.now() ? ToolMessages.CUSTOMER_NOTIFIED
                    : ToolMessages.CUSTOMER_ALREADY_NOTIFIED.formatted(sent.notification().getCreatedAt()));
        });
    }

    @McpTool(name = ToolNames.ESCALATE_TO_HUMAN, description = ToolDescriptions.ESCALATE_TO_HUMAN)
    Acknowledgement escalateToHuman(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.ESCALATED_ORDER_ID, required = false) String orderId,
            @McpToolParam(description = ToolDescriptions.ESCALATION_SUMMARY) String summary,
            @McpToolParam(description = ToolDescriptions.CUSTOMER_EMAIL, required = false) String customerEmail) {
        var id = ToolArguments.optionalOrderId(orderId);
        return guard.run(context, ToolNames.ESCALATE_TO_HUMAN, id, ToolMessages.HANDED_TO_SUPPORT, true, agentId -> {
            // Only a customer-scoped agent's order is looked up, so handing work to a person never depends on the
            // order service being up.
            if (id != null && guard.isCustomerScoped(agentId)) {
                guard.ensureCustomerOwns(agentId, orderOf(id), customerEmail);
            }
            var supportCase = cases.raise(CaseType.HANDOFF, id, summary, AuditEvent.ActorType.AGENT, agentId);
            return new Acknowledgement(supportCase.getId(), ToolMessages.HANDED_OVER);
        });
    }

    private Order orderOf(UUID orderId) {
        return Downstream.call(DownstreamApis.ORDER_SERVICE, () -> orders.getOrder(orderId));
    }

    private PaymentSummary paymentOf(UUID orderId) {
        try {
            return ToolMapper.toSummary(Downstream.call(DownstreamApis.PAYMENT_SERVICE,
                    () -> payments.getPayment(orderId)));
        } catch (DownstreamException failure) {
            return failure.isRetryable() ? ToolMapper.paymentUnknown(failure.getMessage()) : null;
        }
    }

    private ShipmentSummary shipmentOf(UUID orderId) {
        try {
            return ToolMapper.toSummary(Downstream.call(DownstreamApis.SHIPPING_SERVICE,
                    () -> shipping.getShipment(orderId)));
        } catch (DownstreamException unavailable) {
            return null;
        }
    }
}
