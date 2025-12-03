package co.proxydemo.service;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;

public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request);

    default PaymentResponse processPayment(PaymentRequest request, String clientId, String clientSecret) {
        return processPayment(request);
    }
}
