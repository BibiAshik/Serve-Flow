package com.serveflow.entity;

/**
 * BillStatus — tracks the lifecycle of a counter bill (Flow B — QuickBill).
 *
 * WAITING_PAYMENT:  Bill created in UPI mode but no matching payment found yet.
 *                   The matching engine will retry each time a new payment arrives.
 *
 * MATCHED:          A payment has been successfully linked to this bill.
 *                   A token has been generated and printed (or virtual-printed).
 *
 * AMBIGUOUS:        Multiple unmatched UPI payments of the same amount were found.
 *                   The system cannot auto-resolve — biller must manually select
 *                   which payment belongs to this bill by asking the customer for
 *                   their payment timestamp or last 4 digits of UPI reference.
 *
 * CASH_CONFIRMED:   Bill was created with CASH payment mode.
 *                   Token generated immediately — no payment matching involved.
 */
public enum BillStatus {
    WAITING_PAYMENT,
    MATCHED,
    AMBIGUOUS,
    CASH_CONFIRMED,
    CANCELLED
}
