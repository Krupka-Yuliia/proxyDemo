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


        System.out.println("TEST 1: Successful Payment with Product");

        PaymentRequest req1 = new PaymentRequest();
        req1.setAmount(100.00);
        req1.setCardNumber("4242424242424242");
        req1.setIdempotencyKey("567897651");
        req1.setCvv("111");
        req1.setExpiryDate("12/28");
        req1.setMetadata(java.util.Map.of(
                "productId", "1",
                "description", "Ferrari car figure",
                "quantity", "1"
        ));
        paymentService.processPayment(req1, testClient.getClientId(), testClient.getClientSecret());

        System.out.println("\nTEST 2: Card Declined");

        PaymentRequest req2 = new PaymentRequest();
        req2.setAmount(750.50);
        req2.setCardNumber("4000000000000002");
        req2.setIdempotencyKey("561117651");
        req2.setCvv("222");
        req2.setExpiryDate("11/29");
        req2.setMetadata(java.util.Map.of(
                "productId", "2",
                "description", "Phone",
                "quantity", "1"
        ));
        paymentService.processPayment(req2, testClient.getClientId(), testClient.getClientSecret());

        System.out.println("\nTEST 3: Idempotent Request (duplicate)");

        PaymentRequest req3 = new PaymentRequest();
        req3.setAmount(100.00);
        req3.setIdempotencyKey("567897651");
        req3.setCardNumber("4242424242424242");
        req3.setCvv("111");
        req3.setExpiryDate("12/27");
        req3.setMetadata(java.util.Map.of(
                "productId", "1",
                "description", "Ferrari car figure",
                "quantity", "1"
        ));
        paymentService.processPayment(req3, testClient.getClientId(), testClient.getClientSecret());
    }

    private Client initializeTestClient() {
        System.out.println("Initializing test client...");

        Client existingClient = clientRepository.findByClientId("test-client-id")
                .orElse(null);

        if (existingClient != null) {
            System.out.println("Test client already exists: " + existingClient.getName());
            return existingClient;
        }

        Client testClient2 = new Client();
        testClient2.setClientId("test-client-id2");
        testClient2.setClientSecret("test-client-secret2");
        testClient2.setName("Test Client2");
        testClient2.setDescription("Test client for payment proxy demonstration");
        testClient2.setActive(true);

        Client savedClient = clientRepository.save(testClient2);
        System.out.println("Test client created: " + savedClient.getName() + " (ID: " + savedClient.getClientId() + ")\n");

        return savedClient;
    }
}