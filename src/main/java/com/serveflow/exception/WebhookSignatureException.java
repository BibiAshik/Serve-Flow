package com.serveflow.exception;

/**
 * WebhookSignatureException — thrown when a Razorpay webhook request fails HMAC verification.
 *
 * This means the request did NOT come from Razorpay — it may be a spoofed or replayed
 * webhook from an attacker. WebhookService throws this after comparing the computed
 * HMAC-SHA256 against the X-Razorpay-Signature header.
 *
 * GlobalExceptionHandler catches it and returns HTTP 400.
 * The webhook endpoint still returns HTTP 200 to Razorpay even on failure (to prevent
 * retries that could cause confusion) — this is handled in WebhookController.
 */
public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException(String message) {
        super(message);
    }
}
