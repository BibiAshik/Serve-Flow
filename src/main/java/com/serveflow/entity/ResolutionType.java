package com.serveflow.entity;

/**
 * ResolutionType — records how a payment-to-bill match was resolved.
 *
 * Used by MatchAttemptLog to create a full audit trail of every matching decision.
 * This is a key interview talking point — it proves the system's decisions are
 * traceable and defensible.
 *
 * AUTO_MATCH:   The matching engine found exactly one candidate payment for a bill
 *               (or one waiting bill for a payment) and linked them automatically.
 *               No human involved. resolvedBy will be null.
 *
 * MANUAL_MATCH: The biller manually selected which payment to assign to an
 *               AMBIGUOUS bill (multiple candidates of the same amount).
 *               resolvedBy will contain the biller's username.
 */
public enum ResolutionType {
    AUTO_MATCH,
    MANUAL_MATCH
}
