package com.serveflow.controller;

import com.serveflow.entity.Payment;
import com.serveflow.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * WebhookController — receives incoming payment notifications from Razorpay.
 *
 * IMPORTANT DESIGN DECISIONS:
 *
 * 1. This endpoint is PUBLICLY REACHABLE (no JWT required).
 *    Razorpay's servers cannot send a biller JWT — they are external servers.
 *    Security is enforced by signature verification inside WebhookService.
 *    See SecurityConfig: /api/webhook/razorpay → permitAll()
 *
 * 2. This endpoint ALWAYS returns HTTP 200, even if processing fails internally.
 *    WHY: If Razorpay receives a non-200 response, it retries the webhook.
 *    Retries on an internal error would cause duplicate payment processing.
 *    We return 200 always and log any internal errors instead.
 *
 * 3. Raw request body must be read BEFORE any JSON parsing.
 *    The signature is computed over the raw bytes. Once Spring parses JSON,
 *    the byte representation changes and the signature check would fail.
 *    That's why the body parameter type is String.
 *
 * 4. CSRF is disabled for this endpoint (see SecurityConfig).
 *    Razorpay cannot include a CSRF token in their POST request.
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper; // for JSON parsing after signature verification

    public WebhookController(WebhookService webhookService, ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    /**
     * Purpose: Receives and processes Razorpay "payment.captured" webhook events.
     * Input:   body      — raw request body as a String (for signature verification).
     *          signature — value of X-Razorpay-Signature header.
     * Output:  HTTP 200 always (Razorpay must receive 200 to stop retrying).
     */
    @PostMapping("/razorpay")
    public ResponseEntity<String> receiveRazorpayWebhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("receiveRazorpayWebhook: Received webhook event. Signature present: {}", signature != null);

        try {
            // Step 1: Verify the signature BEFORE parsing the JSON.
            // If the signature is missing or invalid, this throws WebhookSignatureException.
            // We catch all exceptions below and still return 200.
            if (signature == null || signature.isEmpty()) {
                log.warn("receiveRazorpayWebhook: No signature header — rejecting.");
                // Still return 200 to prevent Razorpay from retrying (it would fail again).
                return ResponseEntity.ok("rejected: no signature");
            }

            webhookService.verifyRazorpaySignature(body, signature);

            // Step 2: Parse the JSON body to extract payment details.
            // Only process "payment.captured" events — ignore others (refunds, failures, etc.)
            JsonNode root = objectMapper.readTree(body);
            String eventType = root.path("event").asText();

            log.info("receiveRazorpayWebhook: Event type = {}", eventType);

            if (!"payment.captured".equals(eventType)) {
                // Not a payment capture event — acknowledge and ignore.
                log.info("receiveRazorpayWebhook: Ignoring non-capture event: {}", eventType);
                return ResponseEntity.ok("acknowledged");
            }

            // Step 3: Extract payment details from the Razorpay webhook payload.
            // Razorpay's payload structure for payment.captured:
            //   payload.payment.entity.amount    (in paise — divide by 100 for rupees)
            //   payload.payment.entity.id        (Razorpay payment ID)
            //   payload.payment.entity.created_at (Unix timestamp)
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");

            // Amount is in paise (1/100 of rupee). Convert to rupees.
            long amountInPaise = paymentEntity.path("amount").asLong(0);
            BigDecimal amountInRupees = BigDecimal.valueOf(amountInPaise).divide(BigDecimal.valueOf(100));

            // Razorpay payment ID — used as the unique UPI reference ID for idempotency.
            String upiReferenceId = paymentEntity.path("id").asText();

            // Check if this payment belongs to a Campus Bite online order
            JsonNode notesNode = paymentEntity.path("notes");
            String source = notesNode.path("source").asText(null);
            if ("CampusBite".equals(source)) {
                log.info("receiveRazorpayWebhook: Ignoring payment {} because notes.source is CampusBite.", upiReferenceId);
                return ResponseEntity.ok("acknowledged - online order");
            }

            // Timestamp when Razorpay captured the payment.
            long createdAtUnix = paymentEntity.path("created_at").asLong(0);
            LocalDateTime receivedAt = createdAtUnix > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(createdAtUnix), ZoneId.systemDefault())
                    : LocalDateTime.now();

            // Step 4: Process the payment — save and trigger matching.
            Payment savedPayment = webhookService.processIncomingPayment(amountInRupees, upiReferenceId, receivedAt);
            log.info("receiveRazorpayWebhook: Payment processed. ID={}", savedPayment.getId());

        } catch (Exception e) {
            // Internal error — log it but still return 200 to Razorpay.
            // Razorpay must receive 200 to stop retrying. We handle errors internally.
            log.error("receiveRazorpayWebhook: Error processing webhook: {}", e.getMessage(), e);
        }

        // ALWAYS return 200 — this is intentional and non-negotiable.
        // If we return anything else, Razorpay retries, which could cause duplicates.
        return ResponseEntity.ok("received");
    }
}
