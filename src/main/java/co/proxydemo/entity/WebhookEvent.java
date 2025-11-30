package co.proxydemo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String eventId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_webhook_id")
    private ClientWebhook clientWebhook;
    
    @Column(nullable = false)
    private String eventType;
    
    private String transactionId;
    
    private Double amount;
    
    @Column(nullable = false)
    private String status; // PENDING, SENT, FAILED, RETRYING
    
    private String responseCode;
    
    private String errorMessage;
    
    @Column(columnDefinition = "TEXT")
    private String payload;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime sentAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }
}

