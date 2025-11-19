package co.proxydemo.dtos;

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
}
