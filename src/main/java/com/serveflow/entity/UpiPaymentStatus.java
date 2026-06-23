package com.serveflow.entity;

/**
 * UpiPaymentStatus — tracks whether a UPI payment received via webhook
 * has been matched to a bill or is still waiting.
 *
 * NOTE: This is a DIFFERENT enum from OrderPaymentStatus.
 *   - This enum is for the Payment entity (counter UPI payments — Flow B).
 *   - OrderPaymentStatus is for the Order entity (online Razorpay payments — Flow A).
 *
 * UNMATCHED: Payment has been received and stored but not yet linked to any bill.
 *            The matching engine will try to pair it with a waiting bill.
 *
 * MATCHED:   Payment has been successfully claimed by a bill.
 *            The PESSIMISTIC_WRITE lock in claimPaymentForBill() ensures
 *            only ONE bill can ever claim this payment.
 */
public enum UpiPaymentStatus {
    UNMATCHED,
    MATCHED
}
