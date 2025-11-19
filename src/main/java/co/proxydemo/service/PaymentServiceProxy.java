package co.proxydemo.service;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;
import co.proxydemo.dto.WebhookEvent;
import co.proxydemo.entity.Product;
import co.proxydemo.entity.Transaction;
import co.proxydemo.repository.ProductRepository;
import co.proxydemo.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Primary
public class PaymentServiceProxy implements PaymentService {

    private final StripePaymentService realPaymentService;
    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final WebhookService webhookService;

    @Autowired
    public PaymentServiceProxy(StripePaymentService realPaymentService,
                               TransactionRepository transactionRepository,
                               ProductRepository productRepository,
                               WebhookService webhookService) {
        this.realPaymentService = realPaymentService;
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
        this.webhookService = webhookService;
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        System.out.println("\n[PROXY] Payment request received");

        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                System.out.println("[PROXY] Idempotent request detected - returning cached response");
                Transaction tx = existing.get();
                return new PaymentResponse(
                        "SUCCESS".equals(tx.getStatus()),
                        tx.getTransactionId(),
                        "SUCCESS".equals(tx.getStatus()) ? "Payment already processed" : tx.getErrorMessage(),
                        tx.getErrorMessage() != null ? "cached_error" : null,
                        LocalDateTime.now()
                );
            }
        }

        ValidationResult validation = validateRequest(request);
        if (!validation.isValid()) {
            System.out.println("[PROXY] Validation failed: " + validation.getError());
            saveFailedTransaction(request, validation.getError(), idempotencyKey);
            return new PaymentResponse(false, null, validation.getError(), "validation_error", LocalDateTime.now());
        }

        if (request.getProductId() != null) {
            Optional<Product> productOpt = productRepository.findByProductId(request.getProductId());
            if (productOpt.isEmpty()) {
                System.out.println("[PROXY] Product not found: " + request.getProductId());
                saveFailedTransaction(request, "Product not found", idempotencyKey);
                return new PaymentResponse(false, null, "Product not found", "product_not_found", LocalDateTime.now());
            }

            Product product = productOpt.get();
            int requestedQty = request.getQuantity() != null ? request.getQuantity() : 1;

            if (product.getStockQuantity() < requestedQty) {
                System.out.println("[PROXY] Insufficient stock for product: " + product.getName());
                saveFailedTransaction(request, "Insufficient stock", idempotencyKey);
                return new PaymentResponse(false, null, "Insufficient stock available", "insufficient_stock", LocalDateTime.now());
            }

            System.out.println("[PROXY] Product validated: " + product.getName() + " (Stock: " + product.getStockQuantity() + ")");
        }

        logRequest(request);

        System.out.println("[PROXY] Forwarding to Stripe service");
        PaymentResponse response = realPaymentService.processPayment(request);

        if (response.isSuccess() && request.getProductId() != null) {
            updateProductStock(request);
        }

        saveTransaction(request, response, idempotencyKey);

        sendWebhook(response, request);

        logResponse(response);

        System.out.println("[PROXY] Request completed\n");
        return response;
    }

    private ValidationResult validateRequest(PaymentRequest request) {
        if (request.getAmount() <= 0) {
            return new ValidationResult(false, "Amount must be positive");
        }
        if (request.getAmount() > 999999) {
            return new ValidationResult(false, "Amount exceeds limit");
        }
        if (request.getCardNumber() == null || request.getCardNumber().length() < 13) {
            return new ValidationResult(false, "Invalid card number");
        }
        if (request.getCvv() == null || request.getCvv().length() < 3) {
            return new ValidationResult(false, "Invalid CVV");
        }
        if (request.getExpiryDate() == null || !request.getExpiryDate().matches("\\d{2}/\\d{2}")) {
            return new ValidationResult(false, "Invalid expiry date format (use MM/YY)");
        }
        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            return new ValidationResult(false, "Quantity must be positive");
        }
        return new ValidationResult(true, null);
    }

    private void updateProductStock(PaymentRequest request) {
        Optional<Product> productOpt = productRepository.findByProductId(request.getProductId());
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);
            System.out.println("[PROXY] Stock updated for " + product.getName() + ". Remaining: " + product.getStockQuantity());
        }
    }

    private void saveTransaction(PaymentRequest request, PaymentResponse response, String idempotencyKey) {
        String cardLast4 = request.getCardNumber().substring(request.getCardNumber().length() - 4);
        Transaction transaction = new Transaction();
        transaction.setTransactionId(response.getTransactionId());
        transaction.setAmount(request.getAmount());
        transaction.setCardLast4(cardLast4);
        transaction.setStatus(response.isSuccess() ? "SUCCESS" : "FAILED");
        transaction.setErrorMessage(response.isSuccess() ? null : response.getMessage());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIdempotencyKey(idempotencyKey);

        transaction.setProductId(request.getProductId());
        transaction.setDescription(request.getDescription());
        transaction.setQuantity(request.getQuantity());

        if (request.getProductId() != null) {
            productRepository.findByProductId(request.getProductId())
                    .ifPresent(product -> transaction.setProductName(product.getName()));
        }

        transactionRepository.save(transaction);
        System.out.println("[PROXY] Transaction saved to database");
    }

    private void saveFailedTransaction(PaymentRequest request, String error, String idempotencyKey) {
        String cardLast4 = request.getCardNumber() != null && request.getCardNumber().length() >= 4
                ? request.getCardNumber().substring(request.getCardNumber().length() - 4)
                : "0000";
        Transaction transaction = new Transaction();
        transaction.setTransactionId(null);
        transaction.setAmount(request.getAmount());
        transaction.setCardLast4(cardLast4);
        transaction.setStatus("FAILED");
        transaction.setErrorMessage(error);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setProductId(request.getProductId());
        transaction.setDescription(request.getDescription());
        transaction.setQuantity(request.getQuantity());

        transactionRepository.save(transaction);
    }

    private void sendWebhook(PaymentResponse response, PaymentRequest request) {
        if (response.isSuccess()) {
            WebhookEvent event = new WebhookEvent();
            event.setEventId("evt_" + System.currentTimeMillis());
            event.setEventType("payment.success");
            event.setTransactionId(response.getTransactionId());
            event.setAmount(request.getAmount());
            event.setProductId(request.getProductId());
            event.setDescription(request.getDescription());
            event.setTimestamp(LocalDateTime.now());
            webhookService.sendWebhook(event);
        } else {
            WebhookEvent event = new WebhookEvent();
            event.setEventId("evt_" + System.currentTimeMillis());
            event.setEventType("payment.failed");
            event.setTransactionId(null);
            event.setAmount(request.getAmount());
            event.setProductId(request.getProductId());
            event.setDescription(request.getDescription());
            event.setTimestamp(LocalDateTime.now());
            webhookService.sendWebhook(event);
        }
    }

    private void logRequest(PaymentRequest request) {
        String maskedCard = maskCardNumber(request.getCardNumber());
        String productInfo = request.getProductId() != null
                ? ", Product: " + request.getProductId()
                : "";
        System.out.println("[PROXY] Request logged - Amount: $" + request.getAmount() + ", Card: " + maskedCard + productInfo);
    }

    private void logResponse(PaymentResponse response) {
        System.out.println("[PROXY] Response logged - Success: " + response.isSuccess());
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber.length() < 4) return "****";
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String error;

        ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        boolean isValid() {
            return valid;
        }

        String getError() {
            return error;
        }
    }
}