package co.proxydemo.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebhookEvent {
    private String eventId;
    private String eventType;
    private String transactionId;
    private Double amount;
    private LocalDateTime timestamp;
}
