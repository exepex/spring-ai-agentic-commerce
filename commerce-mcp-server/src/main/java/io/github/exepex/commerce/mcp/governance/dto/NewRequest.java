package io.github.exepex.commerce.mcp.governance.dto;

import io.github.exepex.commerce.mcp.governance.RefundRequest;
import java.math.BigDecimal;

/**
 * The refund request a new refund was recorded as, with what it takes the order's refunds to; or, with {@code isNew}
 * false, the request the same refund saved first.
 */
public record NewRequest(RefundRequest request, BigDecimal refundedOrAsked, boolean isNew) {}
