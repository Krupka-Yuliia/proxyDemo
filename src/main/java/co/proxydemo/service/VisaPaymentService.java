package co.proxydemo.service;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VisaPaymentService extends AbstractCardPaymentService {

    @Override
    public String getProviderKey() {
        return "visa";
    }

    @Override
    protected int getProcessingDelayMs() {
        return 600;
    }

    @Override
    protected PaymentResponse simulateProviderResponse(PaymentRequest request) {
        String cardNumber = request.getCardNumber();

        if ("4111111111111111".equals(cardNumber)) {
            return new PaymentResponse(false, null, "Your card was declined", ERROR_CARD_DECLINED, LocalDateTime.now());
        }
        if ("4111111111111112".equals(cardNumber)) {
            return new PaymentResponse(false, null, "Your card has expired", ERROR_EXPIRED_CARD, LocalDateTime.now());
        }
        if ("4111111111111113".equals(cardNumber)) {
            return new PaymentResponse(false, null, "Your card's security code is incorrect", ERROR_INCORRECT_CVC, LocalDateTime.now());
        }
        if ("4111111111111114".equals(cardNumber)) {
            return new PaymentResponse(false, null, "An error occurred while processing your card", ERROR_PROCESSING_ERROR, LocalDateTime.now());
        }
        if ("4111111111111115".equals(cardNumber)) {
            return new PaymentResponse(false, null, "Your card has insufficient funds", ERROR_INSUFFICIENT_FUNDS, LocalDateTime.now());
        }

        if (!isCardValid(request.getExpiryDate())) {
            return new PaymentResponse(false, null, "Your card has expired", ERROR_EXPIRED_CARD, LocalDateTime.now());
        }

        String transactionId = "visa_" + UUID.randomUUID().toString().substring(0, 8);
        return new PaymentResponse(true, transactionId, "Payment processed successfully", null, LocalDateTime.now());
    }
}

