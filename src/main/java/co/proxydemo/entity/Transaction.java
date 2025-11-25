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
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Double amount;
    private String cardLast4;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private String idempotencyKey;
    private String productId;
    private String productName;
    private String description;
    private Integer quantity;
}