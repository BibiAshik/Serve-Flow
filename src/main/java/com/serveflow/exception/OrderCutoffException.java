package com.serveflow.exception;

/**
 * OrderCutoffException — thrown when a student tries to place an online order
 * after the 15-minute ordering cutoff before lunch.
 *
 * OnlineOrderService checks: if now is after (lunchStartTime - 15 minutes), throw this.
 * GlobalExceptionHandler catches it and returns HTTP 400 with the message.
 * The message is shown to the student in the Campus Bite checkout page.
 */
public class OrderCutoffException extends RuntimeException {

    public OrderCutoffException(String message) {
        super(message);
    }
}
