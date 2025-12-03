package co.proxydemo.service;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCardPaymentService implements PaymentProvider {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected static final String ERROR_CARD_DECLINED = "card_declined";
    protected static final String ERROR_EXPIRED_CARD = "expired_card";
    protected static final String ERROR_INCORRECT_CVC = "incorrect_cvc";
    protected static final String ERROR_PROCESSING_ERROR = "processing_error";
    protected static final String ERROR_INSUFFICIENT_FUNDS = "insufficient_funds";
    protected static final String ERROR_NETWORK_ERROR = "network_error";
    protected static final String ERROR_INVALID_REQUEST = "invalid_request_error";

    protected abstract int getProcessingDelayMs();

    protected abstract PaymentResponse simulateProviderResponse(PaymentRequest request);

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        logger.info("Processing payment for amount: {}", request != null ? request.getAmount() : null);

        if (request == null || request.getCardNumber() == null) {
            logger.warn("Invalid payment request received");
            return new PaymentResponse(
                    false,
                    null,
                    "Invalid payment request",
                    ERROR_INVALID_REQUEST,
                    LocalDateTime.now()
            );
        }

        try {
            Thread.sleep(getProcessingDelayMs());

            PaymentResponse response = simulateProviderResponse(request);

            if (response.isSuccess()) {
                logger.info("Payment successful. Transaction ID: {}", response.getTransactionId());
            } else {
                logger.warn("Payment failed: {} (Error: {})", response.getMessage(), response.getErrorCode());
            }

            return response;

        } catch (InterruptedException e) {
            logger.error("Payment processing interrupted", e);
            Thread.currentThread().interrupt();
            return new PaymentResponse(
                    false,
                    null,
                    "Payment processing was interrupted",
                    ERROR_NETWORK_ERROR,
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            logger.error("Unexpected error during payment processing", e);
            return new PaymentResponse(
                    false,
                    null,
                    "An unexpected error occurred while processing the payment",
                    ERROR_PROCESSING_ERROR,
                    LocalDateTime.now()
            );
        }
    }

    protected boolean isCardValid(String expiryDate) {
        if (expiryDate == null || expiryDate.trim().isEmpty()) {
            return false;
        }

        try {
            String[] parts = expiryDate.split("/");
            if (parts.length != 2) {
                return false;
            }

            int month = Integer.parseInt(parts[0]);
            int year = 2000 + Integer.parseInt(parts[1]);

            if (month < 1 || month > 12) {
                return false;
            }

            LocalDate expiry = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
            return !LocalDate.now().isAfter(expiry);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
