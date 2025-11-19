package co.proxydemo.services;

import co.proxydemo.dtos.PaymentRequest;
import co.proxydemo.dtos.PaymentResponse;

public interface PaymentService {
    PaymentResponse processPayment(PaymentRequest request);
}