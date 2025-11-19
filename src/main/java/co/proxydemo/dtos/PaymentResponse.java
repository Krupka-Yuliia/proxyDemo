package co.proxydemo.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {
    private boolean success;
    private String transactionId;
    private String message;
    private String errorCode;
    private LocalDateTime timestamp;


}