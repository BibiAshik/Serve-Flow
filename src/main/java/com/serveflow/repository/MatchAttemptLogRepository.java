package com.serveflow.repository;

import com.serveflow.entity.MatchAttemptLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MatchAttemptLogRepository — data access layer for the payment matching audit trail.
 *
 * Used by:
 *   - PaymentMatchingService: to write a log entry for every matching decision.
 *   - BillerAdminController: to include match type (AUTO/MANUAL) in transaction history.
 */
@Repository
public interface MatchAttemptLogRepository extends JpaRepository<MatchAttemptLog, Long> {

    // Fetches all audit log entries for a specific bill.
    // A bill may have multiple log entries — one per matching attempt.
    // For example: first attempt finds no payment (logged), then a payment arrives
    // and the second attempt succeeds (also logged).
    List<MatchAttemptLog> findByBillIdOrderByResolvedAtDesc(Long billId);
}
