package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.PaymentViews.OutageView;
import io.github.exepex.commerce.payment.PaymentViews.PaymentView;
import io.github.exepex.commerce.payment.PaymentViews.RefundView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PaymentController {

    record ChargeRequest(@NotNull UUID orderId, @NotBlank @Email String customerEmail, @NotNull @Positive BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency, @NotBlank String paymentMethod) {}

    /** The reason and the key are stored with the refund, so they are limited to what their columns hold. */
    record RefundRequest(@NotNull @Positive BigDecimal amount, @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 200) String idempotencyKey) {}

    record OutageRequest(boolean active) {}

    private final PaymentService paymentService;
    private final SimulatedOutage outage;

    /** Answers 201 when the card was charged and 402 with the processor's message when it was declined. */
    @PostMapping("/api/payments")
    ResponseEntity<?> charge(@Valid @RequestBody ChargeRequest request) {
        Payment payment = paymentService.charge(request.orderId(), request.customerEmail(), request.amount(),
                request.currency(), request.paymentMethod());
        return switch (payment.getStatus()) {
            case DECLINED -> ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, payment.getFailureMessage()));
            case SUCCEEDED -> ResponseEntity.status(HttpStatus.CREATED).body(view(payment));
        };
    }

    @GetMapping("/api/payments/{orderId}")
    PaymentView getPayment(@PathVariable UUID orderId) {
        return view(paymentService.getPayment(orderId));
    }

    @PostMapping("/api/payments/{orderId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    RefundView refund(@PathVariable UUID orderId, @Valid @RequestBody RefundRequest request) {
        return PaymentViews.toView(paymentService.refund(orderId, request.amount(), request.reason(),
                request.idempotencyKey()));
    }

    @GetMapping("/api/admin/simulated-outage")
    OutageView getOutage() {
        return new OutageView(outage.isActive());
    }

    @PutMapping("/api/admin/simulated-outage")
    OutageView setOutage(@RequestBody OutageRequest request) {
        outage.setActive(request.active());
        return new OutageView(outage.isActive());
    }

    private PaymentView view(Payment payment) {
        return PaymentViews.toView(payment, paymentService.refundsOf(payment));
    }
}
