package com.serveflow.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — catches all exceptions thrown by any controller or service
 * and returns a clean, structured JSON error response to the frontend.
 *
 * WHY THIS EXISTS:
 *   Without this, Spring Boot would return raw stack traces in the response body —
 *   which leaks implementation details to clients and looks unprofessional.
 *   With this, every error has a consistent JSON format:
 *   { "timestamp": "...", "status": 400, "error": "...", "message": "..." }
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
 * It intercepts exceptions from all controllers in the application.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Purpose: Handles the 15-minute ordering cutoff violation.
     * Input:   exception thrown by OnlineOrderService when past cutoff.
     * Output:  HTTP 400 Bad Request with the cutoff message.
     */
    @ExceptionHandler(OrderCutoffException.class)
    public ResponseEntity<Map<String, Object>> handleOrderCutoffException(OrderCutoffException ex) {
        log.warn("Order cutoff: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Purpose: Handles invalid Razorpay webhook signatures.
     * Input:   exception thrown by WebhookService on signature mismatch.
     * Output:  HTTP 400 Bad Request.
     */
    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<Map<String, Object>> handleWebhookSignatureException(WebhookSignatureException ex) {
        log.error("Webhook signature verification failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Webhook signature verification failed");
    }

    /**
     * Purpose: Handles the concurrency edge case where a payment was claimed between
     *          the search and the lock acquisition.
     * Input:   exception thrown by claimPaymentForBill() in PaymentMatchingService.
     * Output:  HTTP 409 Conflict.
     */
    @ExceptionHandler(PaymentAlreadyClaimedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentAlreadyClaimedException(PaymentAlreadyClaimedException ex) {
        log.warn("Payment already claimed (concurrency): {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Purpose: Handles @Valid / @NotNull / @NotBlank validation failures on DTO fields.
     *          Spring throws this automatically when a controller receives invalid input.
     * Input:   The MethodArgumentNotValidException from Spring's validation layer.
     * Output:  HTTP 400 with a map of field names → error messages.
     *          Example: { "username": "Username is required", "password": "Password is required" }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        // Collect each field's validation error message.
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation failed");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Purpose: Handles IllegalArgumentException — thrown when a business rule is violated
     *          (e.g. trying to resolve a bill that is not in AMBIGUOUS status).
     * Output:  HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Purpose: Handles RuntimeException — a general catch-all for unexpected errors.
     *          Logs the full stack trace (for debugging) but returns only a generic
     *          message to the client (to avoid leaking implementation details).
     * Output:  HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again.");
    }

    /**
     * Purpose: Builds a standard error response map for all handlers above.
     * Input:   status  — the HTTP status code to use.
     *          message — the human-readable error message.
     * Output:  A ResponseEntity with a structured JSON body.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
