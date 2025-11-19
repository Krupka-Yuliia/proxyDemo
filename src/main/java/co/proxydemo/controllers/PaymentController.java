package co.proxydemo.controllers;

import co.proxydemo.dtos.PaymentRequest;
import co.proxydemo.dtos.PaymentResponse;
import co.proxydemo.dtos.WebhookEvent;
import co.proxydemo.entities.Transaction;
import co.proxydemo.repositories.TransactionRepository;
import co.proxydemo.services.PaymentService;
import co.proxydemo.services.WebhookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final TransactionRepository transactionRepository;
    private final WebhookService webhookService;

    @Autowired
    public PaymentController(PaymentService paymentService,
                             TransactionRepository transactionRepository,
                             WebhookService webhookService) {
        this.paymentService = paymentService;
        this.transactionRepository = transactionRepository;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionRepository.findAll());
    }

    @GetMapping("/webhooks")
    public ResponseEntity<List<WebhookEvent>> getWebhooks() {
        return ResponseEntity.ok(webhookService.getEvents());
    }
}
