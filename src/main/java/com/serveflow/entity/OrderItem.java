package com.serveflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OrderItem — represents a single line item within an online student order (Flow A).
 *
 * For example, if a student orders "2x Chicken Fried Rice" and "1x Coca Cola",
 * there will be two OrderItem records linked to one Order.
 *
 * Relationships:
 *   - Belongs to one Order (many-to-one). The @JsonIgnore prevents infinite recursion
 *     when serializing: Order → OrderItems → Order → ...
 *   - References one FoodItem (the menu item that was ordered).
 */
@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The parent order this item belongs to.
    // @JsonIgnore stops Jackson from serializing the Order back-reference
    // (which would cause an infinite loop: Order → items → OrderItem → order → Order...).
    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    // The menu item that was ordered (price is snapshotted in 'price' field below).
    @ManyToOne
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItem foodItem;

    // Number of units of this food item ordered.
    @Column(nullable = false)
    private Integer quantity;

    // Total price for this line item = foodItem.price × quantity.
    // Stored as a snapshot at order time so price changes don't affect past orders.
    @Column(nullable = false)
    private Double price;
}
