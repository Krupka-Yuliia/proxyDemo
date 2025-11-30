package co.proxydemo.controller;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;
import co.proxydemo.entity.Transaction;
import co.proxydemo.repository.TransactionRepository;
import co.proxydemo.service.PaymentService;
import co.proxydemo.service.WebhookService;
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
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }
}
