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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceProxy.class);

    private static final String ERROR_CLIENT_VALIDATION = "client_validation_error";
    private static final String ERROR_VALIDATION = "validation_error";
    private static final String ERROR_CACHED = "cached_error";

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private static final String VISA_CARD_PREFIX = "4111";

    private final StripePaymentService stripePaymentService;
    private final VisaPaymentService visaPaymentService;
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;
    private final WebhookService webhookService;

    @Autowired
    public PaymentServiceProxy(
            StripePaymentService stripePaymentService,
            VisaPaymentService visaPaymentService,
            TransactionRepository transactionRepository,
            ClientRepository clientRepository,
            WebhookService webhookService
    ) {
        this.stripePaymentService = stripePaymentService;
        this.visaPaymentService = visaPaymentService;
        this.transactionRepository = transactionRepository;
        this.clientRepository = clientRepository;
        this.webhookService = webhookService;
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        logger.warn("Client credentials must be supplied via headers (X-Client-Id, X-Client-Secret)");
        return new PaymentResponse(false, null, "Client credentials are required in headers", ERROR_CLIENT_VALIDATION, LocalDateTime.now());
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String clientId, String clientSecret) {
        logger.info("Payment request received for amount: {}", request.getAmount());

        String idempotencyKey = request.getIdempotencyKey();
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            logger.info("Idempotent request detected with key: {}", idempotencyKey);
            Transaction tx = existing.get();
            return buildResponseFromTransaction(tx);
        }

        ValidationResult clientValidation = validateClient(clientId, clientSecret);
        if (!clientValidation.isValid()) {
            logger.warn("Client validation failed: {}", clientValidation.getError());
            return new PaymentResponse(false, null, clientValidation.getError(), ERROR_CLIENT_VALIDATION, LocalDateTime.now());
        }

        ValidationResult validation = validateRequest(request);
        if (!validation.isValid()) {
            logger.warn("Request validation failed: {}", validation.getError());
            saveFailedTransaction(request, validation.getError(), idempotencyKey, clientId);
            return new PaymentResponse(false, null, validation.getError(), ERROR_VALIDATION, LocalDateTime.now());
        }

        logRequest(request);

        PaymentService selectedService = selectPaymentService(request);
        String providerName = getProviderName(selectedService);
        logger.debug("Forwarding payment to {} service", providerName);

        PaymentResponse response = selectedService.processPayment(request);

        saveTransaction(request, response, idempotencyKey, clientId);

        sendWebhook(response, request);

        logResponse(response);

        logger.info("Payment request completed - Success: {}", response.isSuccess());
        return response;
    }

    private ValidationResult validateClient(String clientId, String clientSecret) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return new ValidationResult(false, "Client ID is required");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            return new ValidationResult(false, "Client secret is required");
        }

        Optional<Client> clientOpt = clientRepository.findByClientIdAndClientSecret(clientId, clientSecret);

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
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().trim().isEmpty()) {
            return new ValidationResult(false, "Idempotency key is required");
        }
        return new ValidationResult(true, null);
    }

    private void saveTransaction(
            PaymentRequest request,
            PaymentResponse response,
            String idempotencyKey,
            String clientId
    ) {
        Client client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("Client not found after validation"));

        Transaction transaction = buildTransaction(request, response, client, idempotencyKey);
        transactionRepository.save(transaction);
        logger.debug("Transaction saved with ID: {}", transaction.getId());
    }

    private void saveFailedTransaction(PaymentRequest request, String error, String idempotencyKey, String clientId) {
        if (clientId == null) {
            logger.warn("Cannot save failed transaction: clientId is null");
            return;
        }

        Optional<Client> clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            logger.warn("Cannot save failed transaction: client not found for clientId: {}", clientId);
            return;
        }

        Transaction transaction = buildTransaction(request, null, clientOpt.get(), idempotencyKey);
        transaction.setStatus(STATUS_FAILED);
        transaction.setErrorMessage(error);
        transactionRepository.save(transaction);
        logger.debug("Failed transaction saved");
    }

    private Transaction buildTransaction(PaymentRequest request, PaymentResponse response, Client client, String idempotencyKey) {
        String cardLast4 = extractCardLast4(request.getCardNumber());

        Transaction transaction = new Transaction();
        transaction.setClient(client);
        transaction.setAmount(request.getAmount());
        transaction.setCardLast4(cardLast4);
        transaction.setStatus(response != null && response.isSuccess() ? STATUS_SUCCESS : STATUS_FAILED);
        transaction.setErrorMessage(response != null && !response.isSuccess() ? response.getMessage() : null);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setMetadata(buildMetadata(request));
        if (response != null) {
            transaction.setProviderTransactionId(response.getTransactionId());
        }

        return transaction;
    }

    private String extractCardLast4(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "0000";
        }
        return cardNumber.substring(cardNumber.length() - 4);
    }

    private Map<String, String> buildMetadata(PaymentRequest request) {
        if (request.getMetadata() == null) {
            return new HashMap<>();
        }
        return new HashMap<>(request.getMetadata());
    }

    private PaymentResponse buildResponseFromTransaction(Transaction tx) {
        boolean isSuccess = STATUS_SUCCESS.equals(tx.getStatus());
        return new PaymentResponse(
                isSuccess,
                tx.getProviderTransactionId() != null ? tx.getProviderTransactionId() : tx.getId().toString(),
                isSuccess ? "Payment already processed (cached)" : tx.getErrorMessage(),
                tx.getErrorMessage() != null ? ERROR_CACHED : null,
                LocalDateTime.now()
        );
    }

    private void sendWebhook(PaymentResponse response, PaymentRequest request) {
        WebhookEvent event = buildWebhookEvent(response, request);
        webhookService.sendWebhook(event);
    }

    private WebhookEvent buildWebhookEvent(PaymentResponse response, PaymentRequest request) {
        WebhookEvent event = new WebhookEvent();
        event.setEventId("evt_" + System.currentTimeMillis());
        event.setEventType(response.isSuccess() ? "payment.success" : "payment.failed");
        event.setTransactionId(response.getTransactionId());
        event.setAmount(request.getAmount());
        String productId = null;
        String description = null;
        if (request.getMetadata() != null) {
            productId = request.getMetadata().get("productId");
            description = request.getMetadata().get("description");
        }
        event.setProductId(productId);
        event.setDescription(description);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    private void logRequest(PaymentRequest request) {
        String maskedCard = maskCardNumber(request.getCardNumber());
        logger.debug("Payment request - Amount: {}, Card: {}", request.getAmount(), maskedCard);
    }

    private void logResponse(PaymentResponse response) {
        logger.debug("Payment response - Success: {}, TransactionId: {}", response.isSuccess(), response.getTransactionId());
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }

    private PaymentService selectPaymentService(PaymentRequest request) {
        if (request.getProvider() != null) {
            String key = request.getProvider().trim().toLowerCase();
            if ("visa".equals(key)) {
                return visaPaymentService;
            }
            if ("stripe".equals(key)) {
                return stripePaymentService;
            }
        }
        String cardNumber = request.getCardNumber();
        if (cardNumber != null && cardNumber.startsWith(VISA_CARD_PREFIX)) {
            return visaPaymentService;
        }
        return stripePaymentService;
    }

    private String getProviderName(PaymentService service) {
        if (service instanceof PaymentProvider provider) {
            return provider.getProviderKey();
        }
        return service.getClass().getSimpleName();
    }
}
