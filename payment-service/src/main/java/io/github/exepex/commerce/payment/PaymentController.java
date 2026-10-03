package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.ApiPaths;
import io.github.exepex.commerce.payment.dto.ChargeRequest;
import io.github.exepex.commerce.payment.dto.OutageRequest;
import io.github.exepex.commerce.payment.dto.OutageView;
import io.github.exepex.commerce.payment.dto.PaymentView;
import io.github.exepex.commerce.payment.dto.RefundRequest;
import io.github.exepex.commerce.payment.dto.RefundView;
import jakarta.validation.Valid;
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

    private final PaymentService paymentService;
    private final SimulatedOutage outage;

    /** Answers 201 when the card was charged and 402 with the processor's message when it was declined. */
    @PostMapping(ApiPaths.PAYMENTS)
    ResponseEntity<Object> charge(@Valid @RequestBody ChargeRequest request) {
        var payment = paymentService.charge(request.orderId(), request.customerEmail(), request.amount(),
                request.currency(), request.paymentMethod());
        return switch (payment.getStatus()) {
            case DECLINED -> ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, payment.getFailureMessage()));
            case SUCCEEDED -> ResponseEntity.status(HttpStatus.CREATED).body(view(payment));
        };
    }

    @GetMapping(ApiPaths.PAYMENT)
    PaymentView getPayment(@PathVariable UUID orderId) {
        return view(paymentService.getPayment(orderId));
    }

    @PostMapping(ApiPaths.REFUNDS)
    @ResponseStatus(HttpStatus.CREATED)
    RefundView refund(@PathVariable UUID orderId, @Valid @RequestBody RefundRequest request) {
        return PaymentMapper.toView(paymentService.refund(orderId, request.amount(), request.reason(),
                request.idempotencyKey()));
    }

    @GetMapping(ApiPaths.SIMULATED_OUTAGE)
    OutageView getOutage() {
        return new OutageView(outage.isActive());
    }

    @PutMapping(ApiPaths.SIMULATED_OUTAGE)
    OutageView setOutage(@RequestBody OutageRequest request) {
        outage.setActive(request.active());
        return new OutageView(outage.isActive());
    }

    private PaymentView view(Payment payment) {
        return PaymentMapper.toView(payment, paymentService.refundsOf(payment));
    }
}
