package co.proxydemo;

import co.proxydemo.dto.PaymentRequest;
import co.proxydemo.dto.PaymentResponse;
import co.proxydemo.entity.Client;
import co.proxydemo.entity.Transaction;
import co.proxydemo.repository.ClientRepository;
import co.proxydemo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class PaymentIntegrationTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("proxy_db")
            .withUsername("test")
            .withPassword("test");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/payments";
    }

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        clientRepository.deleteAll();
        Client c = new Client();
        c.setClientId("client-123");
        c.setClientSecret("secret-abc");
        c.setName("Test Client");
        c.setDescription("Integration Test Client");
        c.setActive(true);
        clientRepository.save(c);
    }

    @Test
    void should_process_payment_successfully_via_http_and_persist_transaction() {
        PaymentRequest req = new PaymentRequest();
        req.setAmount(49.99);
        String card = "4111222233334448";
        req.setCardNumber(card);
        req.setCvv("123");
        req.setExpiryDate("12/30");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", "ORD-1001");
        req.setMetadata(metadata);
        req.setProvider("visa");
        req.setIdempotencyKey("idem-success-1001");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Client-Id", "client-123");
        headers.set("X-Client-Secret", "secret-abc");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(req, headers);
        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(baseUrl(), entity, PaymentResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        PaymentResponse body = response.getBody();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getTransactionId()).isNotBlank();
        assertThat(body.getErrorCode()).isNull();
        assertThat(body.getTimestamp()).isNotNull();

        List<Transaction> all = List.of();
        for (int i = 0; i < 20; i++) {
            all = transactionRepository.findAll();
            if (!all.isEmpty()) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        assertThat(all).hasSize(1);
        Transaction tx = all.get(0);
        assertThat(tx.getStatus()).isEqualTo("SUCCESS");
        assertThat(tx.getCardLast4()).isEqualTo(card.substring(card.length() - 4));
        assertThat(tx.getAmount()).isEqualTo(49.99);
        assertThat(tx.getClient()).isNotNull();
        Client persistedClient = clientRepository.findById(tx.getClient().getId()).orElseThrow();
        assertThat(persistedClient.getClientId()).isEqualTo("client-123");
    }

    @Test
    void should_fail_payment_with_declined_card_and_persist_failed_transaction() {
        PaymentRequest req = new PaymentRequest();
        req.setAmount(20.00);
        String card = "4111111111111111";
        req.setCardNumber(card);
        req.setCvv("123");
        req.setExpiryDate("11/30");
        req.setProvider("visa");
        req.setIdempotencyKey("idem-declined-2001");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Client-Id", "client-123");
        headers.set("X-Client-Secret", "secret-abc");

        HttpEntity<PaymentRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(baseUrl(), entity, PaymentResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        PaymentResponse body = response.getBody();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getTransactionId()).isNull();
        assertThat(body.getErrorCode()).isEqualTo("card_declined");
        assertThat(body.getMessage()).containsIgnoringCase("declined");
        assertThat(body.getTimestamp()).isNotNull();

        List<Transaction> all = List.of();
        for (int i = 0; i < 20; i++) {
            all = transactionRepository.findAll();
            if (!all.isEmpty()) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        assertThat(all).hasSize(1);
        Transaction tx = all.get(0);
        assertThat(tx.getStatus()).isEqualTo("FAILED");
        assertThat(tx.getCardLast4()).isEqualTo(card.substring(card.length() - 4));
        assertThat(tx.getAmount()).isEqualTo(20.00);
        assertThat(tx.getClient()).isNotNull();
        Client persistedClient = clientRepository.findById(tx.getClient().getId()).orElseThrow();
        assertThat(persistedClient.getClientId()).isEqualTo("client-123");
    }
}
