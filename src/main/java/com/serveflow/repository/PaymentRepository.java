package com.serveflow.repository;

import com.serveflow.entity.Payment;
import com.serveflow.entity.UpiPaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PaymentRepository — data access layer for UPI payments received via webhook (Flow B).
 *
 * The most critical method here is findByIdWithPessimisticLock(), which is used by
 * PaymentMatchingService.claimPaymentForBill() to ensure that exactly one bill
 * can ever claim a given payment, even under concurrent requests.
 *
 * Without the PESSIMISTIC_WRITE lock:
 *   Two concurrent threads could both find the same UNMATCHED payment,
 *   both decide to claim it, and both set it to MATCHED for different bills —
 *   effectively giving away two tokens for one payment. The lock prevents this.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // CRITICAL — PESSIMISTIC_WRITE LOCK:
    // When claimPaymentForBill() calls this method, the database puts a row-level
    // exclusive lock on the Payment row. No other transaction can read or modify
    // this row until the current transaction commits or rolls back.
    // This is what guarantees one payment → one bill, always.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithPessimisticLock(@Param("id") Long id);

    // Checks for an existing payment with the same Razorpay reference ID.
    // Used by WebhookService for idempotency: if a payment with this ID already
    // exists, we skip saving to prevent duplicates from repeated webhook deliveries.
    Optional<Payment> findByUpiReferenceId(String upiReferenceId);

    // KEY QUERY — used by the matching engine (attemptMatch(Bill bill)):
    // Finds all UNMATCHED payments whose amount equals the bill's amount AND
    // whose receivedAt falls within the configured time window.
    //
    // Parameter explanation:
    //   amount:     the bill amount to search for matching payments
    //   cutoffTime: LocalDateTime = now() minus matchingWindowMinutes
    //               Payments older than this are too old to match with new bills.
    @Query("SELECT p FROM Payment p WHERE p.amount = :amount " +
           "AND p.status = com.serveflow.entity.UpiPaymentStatus.UNMATCHED " +
           "AND p.receivedAt >= :cutoffTime " +
           "ORDER BY p.receivedAt ASC")
    List<Payment> findUnmatchedPaymentsByAmountWithinWindow(
            @Param("amount") BigDecimal amount,
            @Param("cutoffTime") LocalDateTime cutoffTime);

    // Fetches recent payments for display in the QuickBill "Payments Received" zone.
    // Ordered newest first so the biller sees the most recent activity.
    List<Payment> findAllByOrderByReceivedAtDesc();

    // Finds all unmatched payments received after a certain time
    List<Payment> findByStatusAndReceivedAtAfterOrderByReceivedAtDesc(UpiPaymentStatus status, LocalDateTime receivedAt);

    // Finds recent matched payments
    List<Payment> findTop10ByStatusOrderByReceivedAtDesc(UpiPaymentStatus status);

    // Counts unmatched payments received today — for the bottom status bar.
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.receivedAt >= :startOfDay")
    long countByStatusAndReceivedAtAfter(
            @Param("status") UpiPaymentStatus status,
            @Param("startOfDay") LocalDateTime startOfDay);
}
