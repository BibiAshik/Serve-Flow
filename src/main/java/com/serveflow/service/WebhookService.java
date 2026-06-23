package com.serveflow.service;

import com.serveflow.entity.Payment;
import com.serveflow.entity.UpiPaymentStatus;
import com.serveflow.exception.WebhookSignatureException;
import com.serveflow.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * WebhookService — verifies and processes incoming Razorpay webhook events.
 *
 * WEBHOOK FLOW:
 *   1. Customer pays the static UPI QR code at the counter.
 *   2. Razorpay fires a "payment.captured" POST request to /api/webhook/razorpay.
 *   3. WebhookController reads the raw request body and the X-Razorpay-Signature header.
 *   4. verifyRazorpaySignature() checks that the request is genuinely from Razorpay.
 *   5. processIncomingPayment() extracts payment details, checks for duplicates,
 *      saves a new Payment row, and calls PaymentMatchingService.attemptMatch().
 *
 * SECURITY — SIGNATURE VERIFICATION:
 *   Razorpay signs every webhook payload with HMAC-SHA256 using your Razorpay Key Secret.
 *   We compute the same HMAC on our side and compare. If they match, the request is genuine.
 *   If they don't match, the request is rejected — it is not from Razorpay.
 *   This prevents attackers from sending fake webhook events to generate tokens fraudulently.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentMatchingService paymentMatchingService;

    // Razorpay webhook secret — used for HMAC-SHA256 signature verification.
    // Read from application.properties. NEVER hardcoded in source code.
    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    public WebhookService(PaymentRepository paymentRepository,
                          PaymentMatchingService paymentMatchingService) {
        this.paymentRepository = paymentRepository;
        this.paymentMatchingService = paymentMatchingService;
    }

    /**
     * Purpose: Verifies that an incoming webhook request is genuinely from Razorpay.
     *          Uses HMAC-SHA256: compute the signature of the raw payload using the
     *          webhook secret, then compare to the X-Razorpay-Signature header.
     * Input:   payload           — the raw request body as a String (bytes before parsing).
     *          receivedSignature — the value of the X-Razorpay-Signature header.
     * Output:  void if signature is valid.
     * Throws:  WebhookSignatureException if the signature does not match (request is fake).
     *
     * IMPORTANT: This method must receive the RAW request body string — before any JSON
     * parsing. Parsing changes the byte representation and invalidates the signature.
     * That is why WebhookController reads the body as a String, passes it here, and
     * THEN parses the JSON separately.
     */
    public void verifyRazorpaySignature(String payload, String receivedSignature) {
        try {
            // Step 1: Create an HMAC-SHA256 instance.
            // HMAC = Hash-based Message Authentication Code.
            // SHA256 is the hashing algorithm. It produces a 256-bit (32-byte) digest.
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");

            // Step 2: Initialize the HMAC with the webhook secret as the key.
            // The key is the Razorpay webhook secret we configured in our dashboard.
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            hmacSha256.init(secretKey);

            // Step 3: Compute the HMAC of the raw payload bytes.
            // This produces a byte array that represents our expected signature.
            byte[] computedSignatureBytes = hmacSha256.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            // Step 4: Convert the computed bytes to a hex string.
            // Razorpay sends the signature as a lowercase hex string, so we format the same way.
            String computedSignatureHex = HexFormat.of().formatHex(computedSignatureBytes);

            // Step 5: Compare our computed signature to the received signature.
            // We use .equals() instead of == for string comparison (avoid reference comparison).
            if (!computedSignatureHex.equals(receivedSignature)) {
                // Signatures do not match — this request did not come from Razorpay.
                log.error("verifyRazorpaySignature: Signature mismatch. Request rejected.");
                throw new WebhookSignatureException("Razorpay webhook signature verification failed.");
            }

            // Signatures match — request is genuine.
            log.info("verifyRazorpaySignature: Signature verified successfully.");

        } catch (WebhookSignatureException e) {
            throw e; // re-throw our custom exception as-is
        } catch (Exception e) {
            // Any crypto error (algorithm not found, key format error, etc.)
            log.error("verifyRazorpaySignature: Crypto error during verification: {}", e.getMessage());
            throw new WebhookSignatureException("Webhook verification failed due to a crypto error.");
        }
    }

    /**
     * Purpose: Extracts payment details from a verified Razorpay webhook event,
     *          saves a new Payment row (with idempotency check), and triggers matching.
     * Input:   amount        — the payment amount in rupees (from the webhook payload).
     *          upiReferenceId — Razorpay's unique payment reference ID.
     *          receivedAt    — timestamp from the webhook event (or now if not provided).
     * Output:  the saved Payment entity.
     */
    @Transactional
    public Payment processIncomingPayment(BigDecimal amount, String upiReferenceId, LocalDateTime receivedAt) {
        log.info("processIncomingPayment: amount={}, upiRef={}", amount, upiReferenceId);

        // IDEMPOTENCY CHECK:
        // Before saving, check if a Payment with this upiReferenceId already exists.
        // Razorpay may send the same webhook event multiple times (on retry after a non-200 response).
        // We must not create duplicate Payment rows for the same real transaction.
        // (The UNIQUE constraint on upiReferenceId also catches this at the DB level,
        //  but checking here first allows us to return a clean, informative response.)
        if (paymentRepository.findByUpiReferenceId(upiReferenceId).isPresent()) {
            log.warn("processIncomingPayment: Payment with upiReferenceId '{}' already exists. Skipping duplicate.", upiReferenceId);
            return paymentRepository.findByUpiReferenceId(upiReferenceId).get();
        }

        // Create and save the new Payment row.
        Payment payment = new Payment();
        payment.setAmount(amount);
        payment.setUpiReferenceId(upiReferenceId);
        payment.setReceivedAt(receivedAt != null ? receivedAt : LocalDateTime.now());
        payment.setStatus(UpiPaymentStatus.UNMATCHED); // starts as UNMATCHED
        payment.setMatchedBill(null); // no bill matched yet

        Payment savedPayment = paymentRepository.save(payment);
        log.info("processIncomingPayment: Saved Payment id={}", savedPayment.getId());

        // Immediately attempt to match this payment to a waiting bill.
        // This handles the case where a bill was created BEFORE the payment arrived.
        paymentMatchingService.attemptMatch(savedPayment);

        return savedPayment;
    }
}
