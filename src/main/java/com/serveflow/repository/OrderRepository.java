package com.serveflow.repository;

import com.serveflow.entity.Order;
import com.serveflow.entity.OrderPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * OrderRepository — data access layer for the Order entity (Flow A — Campus Bite online orders).
 *
 * Used by:
 *   - OrderService: for order management and status updates.
 *   - OnlineOrderService: for student-specific order queries.
 *   - BillerAdminController: for the biller's "Online Orders" view (incoming Campus Bite orders).
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Fetches all orders sorted by most recent first — used for the admin orders view.
    List<Order> findAllByOrderByOrderDateDesc();

    // Fetches orders by status (PENDING, READY, PICKED_UP) sorted by most recent first.
    // Used by the biller to see which orders are still pending pickup.
    List<Order> findByStatusOrderByOrderDateDesc(String status);

    // Fetches an order by its human-readable token string (old tokenNumber field).
    // Used by the track-order page to look up an order by token.
    Optional<Order> findByTokenNumber(String tokenNumber);

    // Fetches all orders placed by a specific student (by their Google email).
    // Used by the "My Orders" page in Campus Bite — sorted newest first.
    List<Order> findByStudentEmailOrderByPlacedAtDesc(String studentEmail);

    // Fetches orders with a specific Razorpay payment status.
    // Used by the biller's online orders view with status filter.
    List<Order> findByPaymentStatusOrderByPlacedAtDesc(OrderPaymentStatus paymentStatus);
}
