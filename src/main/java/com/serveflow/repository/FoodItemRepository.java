package com.serveflow.repository;

import com.serveflow.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FoodItemRepository — data access layer for the FoodItem (menu item) entity.
 *
 * Used by:
 *   - FoodService: for menu browsing (Campus Bite), item management (QuickBill admin),
 *     and the biller's item-selection dropdown.
 *   - DataInitializer: to seed the initial menu items at startup.
 */
@Repository
public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    // Returns all menu items in a given category (e.g. "Veg", "Non-Veg", "Beverages").
    // Used by the student menu page to display items grouped by category.
    List<FoodItem> findByCategory(String category);
}
