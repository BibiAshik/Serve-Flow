package com.serveflow.entity;

/**
 * PaymentMode — describes how a customer at the counter paid for their bill.
 *
 * Used by the Bill entity (Flow B — QuickBill counter billing).
 *
 * CASH: Customer paid physically with cash. No payment matching needed.
 *       The bill and token are created immediately.
 *
 * UPI:  Customer paid by scanning the static UPI QR code at the counter.
 *       The payment arrives as a Razorpay webhook event. The matching
 *       engine links the payment to the bill.
 */
public enum PaymentMode {
    CASH,
    UPI
}
