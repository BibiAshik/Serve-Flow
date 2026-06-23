package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment — represents a UPI payment received from Razorpay via webhook (Flow B).
 *
 * When a customer at the QuickBill counter scans the static UPI QR code and pays,
 * Razorpay fires a "payment.captured" webhook to /api/webhook/razorpay.
 * WebhookService creates a Payment row for each verified incoming payment.
 *
 * Idempotency guarantee:
 *   The UNIQUE constraint on upiReferenceId ensures the same Razorpay payment
 *   can NEVER be stored twice, even if Razorpay sends the webhook multiple times.
 *   WebhookService also checks for duplicates in code before attempting to save.
 *
 * Concurrency safety:
 *   When the matching engine claims a payment for a bill, it uses PESSIMISTIC_WRITE
 *   row-level locking (see PaymentRepository) to ensure only ONE bill can ever
 *   claim a given payment, even under concurrent requests.
 *
 * Relationships:
 *   - One-to-one with Bill (set once this payment is matched to a counter bill).
 */
@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Amount paid in rupees. Used to search for matching bills of the same amount.
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    // Razorpay's unique reference ID for this payment transaction.
    // UNIQUE constraint: prevents the same real payment from being stored twice
    // even if Razorpay sends duplicate webhook events (which can happen on retry).
    @Column(unique = true, nullable = false)
    private String upiReferenceId;

    // Timestamp when Razorpay received and confirmed this payment.
    // Used by the matching engine's time-window filter.
    private LocalDateTime receivedAt;

    // Whether this payment has been matched to a bill or is still waiting.
    // UNMATCHED: available for the matching engine to claim.
    // MATCHED:   already claimed by a bill; cannot be claimed again.
    @Enumerated(EnumType.STRING)
    private UpiPaymentStatus status;

    // The bill this payment was matched to. Null until a match is made.
    // Set inside claimPaymentForBill() under PESSIMISTIC_WRITE lock.
    @OneToOne
    @JoinColumn(name = "matched_bill_id", nullable = true)
    private Bill matchedBill;
}
