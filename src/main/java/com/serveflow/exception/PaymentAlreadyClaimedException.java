package com.serveflow.exception;

/**
 * PaymentAlreadyClaimedException — thrown inside claimPaymentForBill() when a payment
 * that was found as a candidate has already been claimed by another concurrent request.
 *
 * This is the key concurrency-protection exception. Even with PESSIMISTIC_WRITE locking,
 * there is a brief window between the initial search (finding the payment) and the lock
 * acquisition where another request might claim the payment. After acquiring the lock,
 * claimPaymentForBill() re-checks the payment's status — if it is already MATCHED,
 * it throws this exception rather than silently double-claiming.
 *
 * GlobalExceptionHandler catches it and returns HTTP 409 Conflict.
 * BillingService retries or surfaces the issue to the biller.
 */
public class PaymentAlreadyClaimedException extends RuntimeException {

    public PaymentAlreadyClaimedException(String message) {
        super(message);
    }
}
