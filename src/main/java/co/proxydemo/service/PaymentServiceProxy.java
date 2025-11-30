package co.proxydemo.service;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;
import co.proxydemo.dto.ValidationResult;
import co.proxydemo.dto.WebhookEvent;
import co.proxydemo.entity.Client;
import co.proxydemo.entity.Transaction;
import co.proxydemo.repository.ClientRepository;
import co.proxydemo.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Primary
public class PaymentServiceProxy implements PaymentService {

    private final StripePaymentService realPaymentService;
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;
    private final WebhookService webhookService;

    @Autowired
    public PaymentServiceProxy(
            StripePaymentService realPaymentService,
            TransactionRepository transactionRepository,
            ClientRepository clientRepository,
            WebhookService webhookService
    ) {
        this.realPaymentService = realPaymentService;
        this.transactionRepository = transactionRepository;
        this.clientRepository = clientRepository;
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
                        tx.getId().toString(),
                        "SUCCESS".equals(tx.getStatus()) ? "Payment already processed" : tx.getErrorMessage(),
                        tx.getErrorMessage() != null ? "cached_error" : null,
                        LocalDateTime.now()
                );
            }
        }

        // Validate client credentials
        ValidationResult clientValidation = validateClient(request);
        if (!clientValidation.isValid()) {
            System.out.println("[PROXY] Client validation failed: " + clientValidation.getError());
            return new PaymentResponse(false, null, clientValidation.getError(), "client_validation_error", LocalDateTime.now());
        }

        ValidationResult validation = validateRequest(request);
        if (!validation.isValid()) {
            System.out.println("[PROXY] Validation failed: " + validation.getError());
            saveFailedTransaction(request, validation.getError(), idempotencyKey);
            return new PaymentResponse(false, null, validation.getError(), "validation_error", LocalDateTime.now());
        }


        logRequest(request);

        System.out.println("[PROXY] Forwarding to Stripe service");
        PaymentResponse response = realPaymentService.processPayment(request);

        saveTransaction(request, response, idempotencyKey);

        sendWebhook(response, request);

        logResponse(response);

        System.out.println("[PROXY] Request completed\n");
        return response;
    }

    private ValidationResult validateClient(PaymentRequest request) {
        if (request.getClientId() == null || request.getClientId().trim().isEmpty()) {
            return new ValidationResult(false, "Client ID is required");
        }
        if (request.getClientSecret() == null || request.getClientSecret().trim().isEmpty()) {
            return new ValidationResult(false, "Client secret is required");
        }

        Optional<Client> clientOpt = clientRepository.findByClientIdAndClientSecret(
                request.getClientId(), 
                request.getClientSecret()
        );

        if (clientOpt.isEmpty()) {
            return new ValidationResult(false, "Invalid client credentials");
        }

        Client client = clientOpt.get();
        if (!client.getActive()) {
            return new ValidationResult(false, "Client is inactive");
        }

        return new ValidationResult(true, null);
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

    private void saveTransaction(PaymentRequest request, PaymentResponse response, String idempotencyKey) {
        // Get client for transaction
        Client client = clientRepository.findByClientId(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found")); // Should not happen after validation
        
        String cardLast4 = request.getCardNumber().substring(request.getCardNumber().length() - 4);
        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setAmount(request.getAmount());
        transaction.setCardLast4(cardLast4);
        transaction.setStatus(response.isSuccess() ? "SUCCESS" : "FAILED");
        transaction.setErrorMessage(response.isSuccess() ? null : response.getMessage());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIdempotencyKey(idempotencyKey);

        // Store additional data in metadata
        Map<String, String> metadata = new HashMap<>();
        if (request.getProductId() != null) {
            metadata.put("productId", request.getProductId());
        }
        if (request.getDescription() != null) {
            metadata.put("description", request.getDescription());
        }
        if (request.getQuantity() != null) {
            metadata.put("quantity", request.getQuantity().toString());
        }
        transaction.setMetadata(metadata);

        transactionRepository.save(transaction);
        System.out.println("[PROXY] Transaction saved to database");
    }

    private void saveFailedTransaction(PaymentRequest request, String error, String idempotencyKey) {
        // Only save failed transaction if client is valid (to avoid saving transactions with invalid clients)
        if (request.getClientId() != null) {
            Optional<Client> clientOpt = clientRepository.findByClientId(request.getClientId());
            if (clientOpt.isEmpty()) {
                // Don't save transaction if client doesn't exist
                return;
            }
            
            String cardLast4 = request.getCardNumber() != null && request.getCardNumber().length() >= 4
                    ? request.getCardNumber().substring(request.getCardNumber().length() - 4)
                    : "0000";
            Transaction transaction = new Transaction();
            transaction.setClient(clientOpt.get());
            transaction.setAmount(request.getAmount());
            transaction.setCardLast4(cardLast4);
            transaction.setStatus("FAILED");
            transaction.setErrorMessage(error);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setIdempotencyKey(idempotencyKey);
            
            // Store additional data in metadata
            Map<String, String> metadata = new HashMap<>();
            if (request.getProductId() != null) {
                metadata.put("productId", request.getProductId());
            }
            if (request.getDescription() != null) {
                metadata.put("description", request.getDescription());
            }
            if (request.getQuantity() != null) {
                metadata.put("quantity", request.getQuantity().toString());
            }
            transaction.setMetadata(metadata);

            transactionRepository.save(transaction);
        }
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
        System.out.println("[PROXY] Request logged - Amount: $" + request.getAmount() + ", Card: " + maskedCard);
    }

    private void logResponse(PaymentResponse response) {
        System.out.println("[PROXY] Response logged - Success: " + response.isSuccess());
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber.length() < 4) return "****";
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }
}