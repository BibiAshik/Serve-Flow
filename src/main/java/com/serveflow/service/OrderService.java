package com.serveflow.service;

import com.serveflow.entity.Order;
import com.serveflow.repository.FoodItemRepository;
import com.serveflow.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderService — manages the lifecycle of online orders (Flow A — Campus Bite).
 *
 * Extended from the original CampusBite OrderService to support the new
 * Order fields (studentEmail, paymentStatus, razorpayOrderId, placedAt).
 * The original methods (placeOrder, getAllOrders, etc.) are kept for backward compatibility.
 *
 * Note: Most new Flow A logic lives in OnlineOrderService. This service is kept
 * for admin-level order management (status updates, history views).
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final FoodItemRepository foodItemRepository;

    public OrderService(OrderRepository orderRepository, FoodItemRepository foodItemRepository) {
        this.orderRepository = orderRepository;
        this.foodItemRepository = foodItemRepository;
    }

    /**
     * Purpose: Returns all orders sorted by most recent first.
     *          Used by the biller's Online Orders view to see all Campus Bite pre-orders.
     * Input:   none.
     * Output:  List of all Order entities, sorted newest first.
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc();
    }

    /**
     * Purpose: Returns all orders with status "PENDING".
     *          Used by the biller to see which orders haven't been fulfilled yet.
     * Input:   none.
     * Output:  List of PENDING orders, sorted newest first.
     */
    public List<Order> getPendingOrders() {
        return orderRepository.findByStatusOrderByOrderDateDesc("PENDING");
    }

    /**
     * Purpose: Finds an order by its human-readable token string (old tokenNumber format).
     *          Used by the student track-order page.
     * Input:   tokenNumber — e.g. "ONLINE-1717123456789"
     * Output:  The Order, or null if not found.
     */
    public Order getOrderByToken(String tokenNumber) {
        return orderRepository.findByTokenNumber(tokenNumber).orElse(null);
    }

    /**
     * Purpose: Updates the status of an order (e.g. PENDING → READY → PICKED_UP).
     *          Used by the biller when they mark an order as ready for pickup.
     * Input:   orderId — the ID of the order to update.
     *          status  — the new status string (PENDING, READY, PICKED_UP).
     * Output:  The updated Order entity.
     */
    @Transactional
    public Order updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setStatus(status);
        return orderRepository.save(order);
    }
}
