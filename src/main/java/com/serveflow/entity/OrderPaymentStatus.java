package com.serveflow.entity;

/**
 * OrderPaymentStatus — tracks payment status of an online pre-order (Flow A — Campus Bite).
 *
 * NOTE: This is a DIFFERENT enum from UpiPaymentStatus.
 *   - This enum is for the Order entity (student online payments via Razorpay — Flow A).
 *   - UpiPaymentStatus is for the Payment entity (counter UPI webhook payments — Flow B).
 *
 * PENDING: Order created, Razorpay order initiated, but student has not yet
 *          completed the checkout or payment is not yet confirmed.
 *
 * PAID:    Razorpay has confirmed the payment. The backend verified the signature.
 *          A token has been generated and linked to this order.
 *
 * FAILED:  Payment was initiated but failed (declined, timeout, or student cancelled).
 *          The student can retry.
 */
public enum OrderPaymentStatus {
    PENDING,
    PAID,
    FAILED
}
