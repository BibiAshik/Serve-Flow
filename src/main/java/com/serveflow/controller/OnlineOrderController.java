package com.serveflow.controller;

import com.serveflow.dto.request.OnlineOrderCreateRequestDTO;
import com.serveflow.dto.request.OnlinePaymentVerifyRequestDTO;
import com.serveflow.dto.response.OnlineOrderResponseDTO;
import com.serveflow.dto.response.TokenResponseDTO;
import com.serveflow.service.OnlineOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OnlineOrderController — handles Campus Bite student online ordering (Flow A).
 *
 * All endpoints require ROLE_STUDENT.
 * The student's email is ALWAYS extracted from the JWT using Authentication.getName()
 * — it is NEVER accepted as a request parameter. This prevents students from
 * submitting orders or payments on behalf of other students.
 *
 * Endpoints:
 *   POST /api/online/orders                — place a new online order
 *   POST /api/online/orders/verify-payment — confirm payment after Razorpay checkout
 *   GET  /api/online/my-orders             — student's order and token history
 */
@RestController
@RequestMapping("/api/online")
@PreAuthorize("hasRole('STUDENT')") // all methods require ROLE_STUDENT
public class OnlineOrderController {

    private final OnlineOrderService onlineOrderService;

    public OnlineOrderController(OnlineOrderService onlineOrderService) {
        this.onlineOrderService = onlineOrderService;
    }

    /**
     * Purpose: Places a new online pre-order for the authenticated student.
     *          Enforces the 15-minute cutoff before lunch.
     *          Creates a Razorpay Order and returns the orderId for checkout.
     * Input:   dto            — items and pickup time from the checkout form.
     *          authentication — JWT-based, provides the student's email.
     * Output:  OnlineOrderResponseDTO including razorpayOrderId for the frontend checkout modal.
     */
    @PostMapping("/orders")
    public ResponseEntity<OnlineOrderResponseDTO> createOrder(
            @Valid @RequestBody OnlineOrderCreateRequestDTO dto,
            Authentication authentication) {

        // Extract student email from the JWT. NEVER from the request body.
        String studentEmail = authentication.getName();

        OnlineOrderResponseDTO order = onlineOrderService.createOnlineOrder(dto, studentEmail);
        return ResponseEntity.ok(order);
    }

    /**
     * Purpose: Verifies the Razorpay payment after the student completes checkout.
     *          Marks the order as PAID and generates a food pickup token.
     * Input:   dto            — Razorpay orderId, paymentId, and signature from the JS callback.
     *          authentication — JWT-based, ensures the student can only confirm their own payment.
     * Output:  TokenResponseDTO — the student's token to show at the counter.
     */
    @PostMapping("/orders/verify-payment")
    public ResponseEntity<TokenResponseDTO> verifyPayment(
            @Valid @RequestBody OnlinePaymentVerifyRequestDTO dto,
            Authentication authentication) {

        String studentEmail = authentication.getName();

        TokenResponseDTO token = onlineOrderService.processOnlinePayment(
                dto.getRazorpayOrderId(),
                dto.getRazorpayPaymentId(),
                dto.getRazorpaySignature(),
                studentEmail
        );

        return ResponseEntity.ok(token);
    }

    /**
     * Purpose: Returns the authenticated student's order history and tokens.
     *          Shown on the Campus Bite "My Orders" page.
     * Input:   authentication — JWT-based, identifies the student.
     * Output:  List of OnlineOrderResponseDTO — all orders for this student, newest first.
     */
    @GetMapping("/my-orders")
    public ResponseEntity<List<OnlineOrderResponseDTO>> getMyOrders(Authentication authentication) {

        String studentEmail = authentication.getName();
        List<OnlineOrderResponseDTO> orders = onlineOrderService.getStudentOrders(studentEmail);

        return ResponseEntity.ok(orders);
    }

    /**
     * Purpose: Marks an order as SERVED.
     */
    @PutMapping("/orders/{id}/serve")
    public ResponseEntity<Void> serveOrder(@PathVariable Long id, Authentication authentication) {
        onlineOrderService.markOrderAsServed(id, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
