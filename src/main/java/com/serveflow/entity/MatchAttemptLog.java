package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MatchAttemptLog — audit trail for every payment-to-bill matching decision.
 *
 * This table is one of the most important in the entire system from an audit
 * and interview perspective. Every time the PaymentMatchingService attempts to
 * match a bill to a payment (whether it succeeds, finds ambiguity, or is still
 * waiting), a log entry is written or updated here.
 *
 * This proves:
 *   - The system's decisions are fully traceable and defensible.
 *   - Auto-matches are distinguishable from biller-resolved manual matches.
 *   - No payment was silently double-claimed (each claim has a logged reason).
 *
 * Relationships:
 *   - Many-to-one with Bill (one bill can have multiple log entries — one per attempt).
 *   - Many-to-one with Payment (the payment that was ultimately resolved, if any).
 */
@Entity
@Table(name = "match_attempt_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchAttemptLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The bill that the matching engine was trying to resolve.
    @ManyToOne
    @JoinColumn(name = "bill_id")
    private Bill bill;

    // Comma-separated list of Payment IDs that were considered as candidates
    // during this matching attempt. Example: "12,13,17"
    // Empty string if no candidates were found (bill stayed WAITING_PAYMENT).
    private String candidatePaymentIds;

    // The payment that was ultimately selected and claimed for this bill.
    // Null if the match has not yet been resolved (still WAITING_PAYMENT or AMBIGUOUS).
    @ManyToOne
    @JoinColumn(name = "resolved_payment_id", nullable = true)
    private Payment resolvedPayment;

    // How the match was resolved: AUTO_MATCH by the engine, or MANUAL_MATCH by the biller.
    // Null if not yet resolved.
    @Enumerated(EnumType.STRING)
    private ResolutionType resolutionType;

    // Username of the biller who manually resolved the match (MANUAL_MATCH only).
    // Null for AUTO_MATCH or unresolved entries.
    private String resolvedBy;

    // Timestamp of when the resolution was made.
    // Null if not yet resolved.
    private LocalDateTime resolvedAt;
}
