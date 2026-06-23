package com.serveflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OnlinePaymentVerifyRequestDTO — carries Razorpay's response after a student completes checkout.
 *
 * Used as the request body for POST /api/online/orders/verify-payment.
 *
 * When the student completes payment in the Razorpay modal, Razorpay sends these
 * three values to the frontend JavaScript callback. The frontend immediately sends
 * them to our backend for signature verification.
 *
 * Security: OnlineOrderService computes HMAC-SHA256(razorpayOrderId + "|" + razorpayPaymentId)
 * using the Razorpay Key Secret and compares it to razorpaySignature.
 * If they match, the payment is genuine and we mark the order as PAID.
 */
@Data
public class OnlinePaymentVerifyRequestDTO {

    @NotBlank(message = "Razorpay Order ID is required")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay Payment ID is required")
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay Signature is required")
    private String razorpaySignature;
}
