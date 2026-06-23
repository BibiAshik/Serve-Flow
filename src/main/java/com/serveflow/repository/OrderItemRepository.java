package com.serveflow.repository;

import com.serveflow.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * OrderItemRepository — data access layer for individual line items within an online order.
 *
 * In most cases, OrderItems are accessed through their parent Order (via the OneToMany
 * relationship and CascadeType.ALL), so this repository has no custom methods needed
 * in the current phase.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // Standard JpaRepository methods (findById, save, findAll, delete) are sufficient.
}
