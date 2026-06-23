package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order — represents an online pre-order placed by a student via Campus Bite (Flow A).
 *
 * This entity covers the FULL online ordering lifecycle:
 *   Student browses menu → adds items → checks out → pays via Razorpay →
 *   backend verifies payment → token generated → student picks up food.
 *
 * Relationships:
 *   - Has many OrderItem records (the line items in the order).
 *   - Links to a Token once payment is confirmed (via tokenId foreign key).
 *
 * NOTE: The 15-minute cutoff before lunch is enforced in OnlineOrderService,
 * NOT stored as a column here. It is a pure service-layer rule.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── ORIGINAL FIELDS (kept from CampusBite) ──────────────────────────────

    // Human-readable token string from the old system.
    // Still stored for backward compatibility but new flows use the Token entity.
    @Column(nullable = false, unique = true)
    private String tokenNumber;

    // Student's full name (entered at order time in the old flow).
    @Column(nullable = false)
    private String studentName;

    // Student's roll number (entered at order time in the old flow).
    @Column(nullable = false)
    private String rollNumber;

    // Order status for the admin view: PENDING, READY, PICKED_UP.
    @Column(nullable = false)
    private String status;

    // Total amount in rupees for all items combined.
    @Column(nullable = false)
    private Double totalAmount;

    // Preferred pickup time entered by the student.
    private String pickupTime;

    // Timestamp when the order was first created.
    private LocalDateTime orderDate;

    // Line items — each FoodItem + quantity in this order.
    // CascadeType.ALL: saving an Order automatically saves its OrderItems.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    // ── NEW FIELDS (Flow A — Campus Bite online payment) ─────────────────────

    // The student's Google email. Domain must end with @sairamtap.edu.in.
    // Set by OnlineOrderService using the authenticated principal — never from request body.
    private String studentEmail;

    // Payment lifecycle for this specific online order.
    // PENDING → student opened checkout but not paid yet.
    // PAID    → Razorpay confirmed payment, token generated.
    // FAILED  → payment failed or student cancelled.
    @Enumerated(EnumType.STRING)
    private OrderPaymentStatus paymentStatus;

    // The Razorpay Order ID returned when we call the Razorpay Orders API.
    // Used by the frontend to open the Razorpay checkout modal.
    // Nullable until payment is initiated via OnlineOrderService.
    private String razorpayOrderId;

    // The Razorpay Payment ID returned after the student completes checkout.
    // Stored after OnlineOrderService verifies the payment signature.
    // Nullable until payment is confirmed.
    private String razorpayPaymentId;

    // Foreign key to the Token generated for this order once payment is confirmed.
    // Nullable until the order is PAID and a token is created.
    @Column(name = "token_id")
    private Long tokenId;

    // Precise timestamp when the student placed this order.
    // Used for ordering history display and audit purposes.
    private LocalDateTime placedAt;
}
