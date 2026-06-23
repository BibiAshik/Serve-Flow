package com.serveflow.dto.response;

import com.serveflow.entity.OrderPaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OnlineOrderResponseDTO — carries online order details to the Campus Bite frontend.
 *
 * Returned by POST /api/online/orders after an order is created.
 * The razorpayOrderId is critical — it is passed to the Razorpay checkout modal
 * in student.js to initiate the payment flow.
 *
 * Also returned in GET /api/online/my-orders (list of student's order history).
 */
@Data
public class OnlineOrderResponseDTO {

    private Long id;
    private String studentEmail;
    private Double totalAmount;
    private OrderPaymentStatus paymentStatus;  // PENDING, PAID, FAILED
    private String razorpayOrderId;            // passed to Razorpay checkout modal in student.js
    private LocalDateTime placedAt;
    private String status;                     // PENDING, READY, PICKED_UP
    private String itemSummary;                // e.g., "2x Chapati, 1x Veg Fried Rice"
    private String pickupTime;                 // Preferred pickup time

    // The token details — only populated once payment is confirmed (status = PAID).
    private TokenResponseDTO token;
}
