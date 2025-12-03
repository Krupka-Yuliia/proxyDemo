package co.proxydemo.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequest {
    private double amount;
    private String cardNumber;
    private String cvv;
    private String expiryDate;
    private String idempotencyKey;
    private Map<String, String> metadata;
    private String provider;
}
