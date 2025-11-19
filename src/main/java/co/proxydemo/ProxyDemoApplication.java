package co.proxydemo;

import co.proxydemo.dtos.PaymentRequest;
import co.proxydemo.services.PaymentService;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@AllArgsConstructor
@SpringBootApplication
public class ProxyDemoApplication implements CommandLineRunner {

    private PaymentService paymentService;

    public static void main(String[] args) {
        SpringApplication.run(ProxyDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║  PAYMENT PROXY PATTERN DEMONSTRATION          ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        System.out.println("TEST 1: Successful Payment");
        System.out.println("═══════════════════════════");
        PaymentRequest req1 = new PaymentRequest(150.00, "4242424242424242", "123", "12/28", "key-001");
        paymentService.processPayment(req1);

        System.out.println("\nTEST 2: Card Declined");
        System.out.println("═══════════════════════");
        PaymentRequest req2 = new PaymentRequest(75.50, "4000000000000002", "456", "03/27", "key-002");
        paymentService.processPayment(req2);

        System.out.println("\nTEST 3: Idempotent Request (duplicate)");
        System.out.println("════════════════════════════════════════");
        PaymentRequest req3 = new PaymentRequest(150.00, "4242424242424242", "123", "12/28", "key-001");
        paymentService.processPayment(req3);
    }
}