package io.github.exepex.commerce.shipping.dto;

import io.github.exepex.commerce.shipping.Shipment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What the carrier reports about a shipped parcel. {@code deliveryProblem} says why a parcel was not delivered, with a
 * plain default when it is left out; it is not used for a delivered one.
 */
public record CarrierReportRequest(@NotNull Shipment.Status outcome, @Size(max = 500) String deliveryProblem) {}
