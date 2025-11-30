package co.proxydemo;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.entity.Client;
import co.proxydemo.repository.ClientRepository;
import co.proxydemo.service.PaymentService;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@AllArgsConstructor
@SpringBootApplication
public class ProxyDemoApplication implements CommandLineRunner {

    private PaymentService paymentService;
    private ClientRepository clientRepository;

    public static void main(String[] args) {
        SpringApplication.run(ProxyDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        Client testClient = initializeTestClient();

        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║  PAYMENT PROXY PATTERN DEMONSTRATION          ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        System.out.println("TEST 1: Successful Payment with Product");
        System.out.println("═════════════════════════════════════════════");
        PaymentRequest req1 = new PaymentRequest();
        req1.setClientId(testClient.getClientId());
        req1.setClientSecret(testClient.getClientSecret());
        req1.setAmount(150.00);
        req1.setCardNumber("4242424242424242");
        req1.setCvv("123");
        req1.setExpiryDate("12/28");
        req1.setIdempotencyKey("key-001");
        req1.setProductId("1");
        req1.setDescription("Wireless Headphones");
        req1.setQuantity(1);
        paymentService.processPayment(req1);

        System.out.println("\nTEST 2: Card Declined");
        System.out.println("═══════════════════════");
        PaymentRequest req2 = new PaymentRequest();
        req2.setClientId(testClient.getClientId());
        req2.setClientSecret(testClient.getClientSecret());
        req2.setAmount(75.50);
        req2.setCardNumber("4000000000000002");
        req2.setCvv("456");
        req2.setExpiryDate("03/27");
        req2.setIdempotencyKey("key-002");
        req2.setProductId("2");
        req2.setDescription("Smart Watch");
        req2.setQuantity(1);
        paymentService.processPayment(req2);

        System.out.println("\nTEST 3: Idempotent Request (duplicate)");
        System.out.println("════════════════════════════════════════");
        PaymentRequest req3 = new PaymentRequest();
        req3.setClientId(testClient.getClientId());
        req3.setClientSecret(testClient.getClientSecret());
        req3.setAmount(150.00);
        req3.setCardNumber("4242424242424242");
        req3.setCvv("123");
        req3.setExpiryDate("12/28");
        req3.setIdempotencyKey("key-001");
        req3.setProductId("1");
        req3.setDescription("Wireless Headphones");
        req3.setQuantity(1);
        paymentService.processPayment(req3);

        System.out.println("\nTEST 4: Insufficient Stock");
        System.out.println("═══════════════════════════");
        PaymentRequest req4 = new PaymentRequest();
        req4.setClientId(testClient.getClientId());
        req4.setClientSecret(testClient.getClientSecret());
        req4.setAmount(299.99);
        req4.setCardNumber("4242424242424242");
        req4.setCvv("789");
        req4.setExpiryDate("06/29");
        req4.setIdempotencyKey("key-004");
        req4.setProductId("3");
        req4.setDescription("Gaming Laptop");
        req4.setQuantity(10);
        paymentService.processPayment(req4);

        System.out.println("\nTEST 5: Product Not Found");
        System.out.println("═══════════════════════════");
        PaymentRequest req5 = new PaymentRequest();
        req5.setClientId(testClient.getClientId());
        req5.setClientSecret(testClient.getClientSecret());
        req5.setAmount(50.00);
        req5.setCardNumber("4242424242424242");
        req5.setCvv("321");
        req5.setExpiryDate("09/27");
        req5.setIdempotencyKey("key-005");
        req5.setProductId("999");
        req5.setDescription("Non-existent Product");
        req5.setQuantity(1);
        paymentService.processPayment(req5);
    }

    private Client initializeTestClient() {
        System.out.println("Initializing test client...");

        Client existingClient = clientRepository.findByClientId("test-client-id")
                .orElse(null);

        if (existingClient != null) {
            System.out.println("Test client already exists: " + existingClient.getName());
            return existingClient;
        }

        Client testClient = new Client();
        testClient.setClientId("test-client-id");
        testClient.setClientSecret("test-client-secret");
        testClient.setName("Test Client");
        testClient.setDescription("Test client for payment proxy demonstration");
        testClient.setActive(true);

        Client savedClient = clientRepository.save(testClient);
        System.out.println("Test client created: " + savedClient.getName() + " (ID: " + savedClient.getClientId() + ")\n");

        return savedClient;
    }
}