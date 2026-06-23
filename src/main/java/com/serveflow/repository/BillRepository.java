package com.serveflow.repository;

import com.serveflow.entity.Bill;
import com.serveflow.entity.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BillRepository — data access layer for counter bills (Flow B — QuickBill).
 *
 * The most important queries here are used by the PaymentMatchingService
 * to find bills that are waiting for a UPI payment to arrive.
 */
@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    // Fetches all bills in a given status — used by getLiveBillingStatus()
    // to populate the "Waiting for Payment" and "Ambiguous" zones on the billing screen.
    List<Bill> findByStatusOrderByCreatedAtAsc(BillStatus status);

    // Fetches the most recently created bills regardless of status.
    // Used by the transaction history page with a limit on rows returned.
    List<Bill> findAllByOrderByCreatedAtDesc();

    // KEY QUERY — used by the matching engine (attemptMatch(Payment payment)):
    // Finds all UPI bills in WAITING_PAYMENT status whose amount equals the incoming
    // payment's amount AND whose createdAt falls within the configured time window.
    //
    // Parameter explanation:
    //   amount:   the payment amount to match against
    //   cutoffTime: LocalDateTime = now() minus matchingWindowMinutes
    //              Bills created before this time are too old to match.
    @Query("SELECT b FROM Bill b WHERE b.amount = :amount " +
           "AND b.status = com.serveflow.entity.BillStatus.WAITING_PAYMENT " +
           "AND b.paymentMode = com.serveflow.entity.PaymentMode.UPI " +
           "AND b.createdAt >= :cutoffTime " +
           "ORDER BY b.createdAt ASC")
    List<Bill> findWaitingBillsByAmountWithinWindow(
            @Param("amount") BigDecimal amount,
            @Param("cutoffTime") LocalDateTime cutoffTime);

    // Counts all bills created today — used in the bottom status bar of QuickBill.
    @Query("SELECT COUNT(b) FROM Bill b WHERE b.createdAt >= :startOfDay")
    long countBillsCreatedAfter(@Param("startOfDay") LocalDateTime startOfDay);

    // Finds bills that have been sitting in the given statuses since before the cutoff time.
    // Used by the scheduled task to cancel abandoned bills.
    List<Bill> findByStatusInAndCreatedAtBefore(List<BillStatus> statuses, LocalDateTime cutoffTime);
}
