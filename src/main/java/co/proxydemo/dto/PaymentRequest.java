package co.proxydemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequest {
    private String clientId;
    private String clientSecret;
    private double amount;
    private String cardNumber;
    private String cvv;
    private String expiryDate;
    private String idempotencyKey;
    private String productId;
    private String description;
    private Integer quantity;
}