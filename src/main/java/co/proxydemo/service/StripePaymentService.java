package co.proxydemo.service;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class StripePaymentService implements PaymentService {

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        System.out.println("[STRIPE] Processing payment for amount: $" + request.getAmount());

        try {
            Thread.sleep(800);

            PaymentResponse response = simulateStripeResponse(request);

            if (response.isSuccess()) {
                System.out.println("[STRIPE] Payment successful. Transaction ID: " + response.getTransactionId());
            } else {
                System.out.println("[STRIPE] Payment failed: " + response.getMessage());
            }

            return response;

        } catch (InterruptedException e) {
            return new PaymentResponse(false, null, "Network error", "network_error", LocalDateTime.now());
        }
    }

    private PaymentResponse simulateStripeResponse(PaymentRequest request) {
        String cardNumber = request.getCardNumber();

        if (cardNumber.equals("4000000000000002")) {
            return new PaymentResponse(false, null, "Card declined", "card_declined", LocalDateTime.now());
        }
        if (cardNumber.equals("4000000000000069")) {
            return new PaymentResponse(false, null, "Card expired", "expired_card", LocalDateTime.now());
        }
        if (cardNumber.equals("4000000000000127")) {
            return new PaymentResponse(false, null, "Incorrect CVC", "incorrect_cvc", LocalDateTime.now());
        }
        if (cardNumber.equals("4000000000000119")) {
            return new PaymentResponse(false, null, "Processing error", "processing_error", LocalDateTime.now());
        }
        if (cardNumber.equals("4000000000000341")) {
            return new PaymentResponse(false, null, "Insufficient funds", "insufficient_funds", LocalDateTime.now());
        }

        if (!isCardValid(request.getExpiryDate())) {
            return new PaymentResponse(false, null, "Card expired", "expired_card", LocalDateTime.now());
        }

        String transactionId = "txn_" + UUID.randomUUID().toString().substring(0, 8);
        return new PaymentResponse(true, transactionId, "Payment processed successfully", null, LocalDateTime.now());
    }

    private boolean isCardValid(String expiryDate) {
        try {
            String[] parts = expiryDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = 2000 + Integer.parseInt(parts[1]);

            LocalDate expiry = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
            return expiry.isAfter(LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }
}