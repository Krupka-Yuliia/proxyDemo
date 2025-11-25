package co.proxydemo;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.entity.Product;
import co.proxydemo.repository.ProductRepository;
import co.proxydemo.service.PaymentService;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@AllArgsConstructor
@SpringBootApplication
public class ProxyDemoApplication implements CommandLineRunner {

    private PaymentService paymentService;
    private ProductRepository productRepository;

    public static void main(String[] args) {
        SpringApplication.run(ProxyDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        initializeProducts();

        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║  PAYMENT PROXY PATTERN DEMONSTRATION          ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        System.out.println("TEST 1: Successful Payment with Product");
        System.out.println("═════════════════════════════════════════════");
        PaymentRequest req1 = new PaymentRequest();
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

    private void initializeProducts() {
        System.out.println("Initializing sample products...");

        Product p1 = new Product();
        p1.setName("Wireless Headphones");
        p1.setDescription("High-quality noise-cancelling headphones");
        p1.setPrice(150.00);
        p1.setStockQuantity(50);
        p1.setCategory("Electronics");
        productRepository.save(p1);

        Product p2 = new Product();
        p2.setName("Smart Watch");
        p2.setDescription("Fitness tracking smartwatch with GPS");
        p2.setPrice(299.99);
        p2.setStockQuantity(30);
        p2.setCategory("Wearables");
        productRepository.save(p2);

        Product p3 = new Product();
        p3.setName("Gaming Laptop");
        p3.setDescription("High-performance gaming laptop");
        p3.setPrice(1499.99);
        p3.setStockQuantity(5);
        p3.setCategory("Computers");
        productRepository.save(p3);

        Product p4 = new Product();
        p4.setName("Mechanical Keyboard");
        p4.setDescription("RGB mechanical keyboard");
        p4.setPrice(129.99);
        p4.setStockQuantity(100);
        p4.setCategory("Accessories");
        productRepository.save(p4);

        System.out.println("Sample products initialized!\n");
    }
}