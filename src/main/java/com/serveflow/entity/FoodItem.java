package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FoodItem — represents a single menu item in the canteen.
 *
 * Used by both portals:
 *   - Campus Bite: students browse and add items to their cart for online pre-orders.
 *   - QuickBill:   the biller selects items from a dropdown when creating counter bills.
 *                  The biller never types item names manually — always picks from this table.
 *
 * Seed data is loaded at startup by DataInitializer.
 *
 * Relationships:
 *   - Referenced by OrderItem (many items in an online order).
 *   - Referenced by Bill (one item per counter bill; nullable if biller typed custom).
 */
@Entity
@Table(name = "food_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Display name of the food item (e.g. "Chicken Fried Rice").
    @Column(nullable = false)
    private String name;

    // Category for grouping on the student menu (e.g. "Veg", "Non-Veg", "Beverages").
    @Column(nullable = false)
    private String category;

    // Price per unit in Indian Rupees. Used as the default unit rate in billing.
    @Column(nullable = false)
    private Double price;

    // Path or filename of the item's photo (served from static/images/).
    private String imageUrl;

    // Current available quantity. Decremented when a student places an online order.
    private Integer quantityAvailable;

    // True if the item is vegetarian. Used to show the green/red veg indicator dot.
    @Column(nullable = false)
    private Boolean isVeg;

    // Short description displayed on the menu card.
    @Column(length = 500)
    private String description;
}
