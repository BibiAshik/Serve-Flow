package com.serveflow.controller;

import com.serveflow.dto.response.FoodItemResponseDTO;
import com.serveflow.entity.FoodItem;
import com.serveflow.service.FoodService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FoodController — manages food menu items for both portals.
 *
 * Public endpoints (no JWT required):
 *   GET /api/food/menu       — Campus Bite student menu browsing
 *
 * Biller-only endpoints (ROLE_BILLER required):
 *   GET    /api/food/dropdown — biller's item-selection dropdown
 *   POST   /api/food          — add a new menu item (admin action)
 *   PUT    /api/food/{id}     — update a menu item
 *   DELETE /api/food/{id}     — remove a menu item
 */
@RestController
@RequestMapping("/api/food")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    /**
     * Purpose: Returns all menu items for the Campus Bite student menu page.
     *          Publicly accessible — students browse the menu before or after login.
     * Input:   none.
     * Output:  List of FoodItemResponseDTO with name, category, price, image, isVeg, description.
     */
    @GetMapping("/menu")
    public ResponseEntity<List<FoodItemResponseDTO>> getMenu() {
        return ResponseEntity.ok(foodService.getAllFoodItemDTOs());
    }

    /**
     * Purpose: Returns all menu items for the biller's item-selection dropdown in QuickBill.
     *          Biller selects from this list when creating a counter bill.
     *          Requires ROLE_BILLER — the dropdown is only in QuickBill.
     * Input:   none.
     * Output:  List of FoodItemResponseDTO (id, name, price most important for the dropdown).
     */
    @GetMapping("/dropdown")
    @PreAuthorize("hasRole('BILLER')")
    public ResponseEntity<List<FoodItemResponseDTO>> getDropdown() {
        return ResponseEntity.ok(foodService.getAllFoodItemsForDropdown());
    }

    /**
     * Purpose: Creates a new menu item (admin action — biller uses the admin panel).
     * Input:   FoodItem entity in the request body.
     * Output:  The saved FoodItem entity.
     */
    @PostMapping
    @PreAuthorize("hasRole('BILLER')")
    public ResponseEntity<FoodItem> addFoodItem(@RequestBody FoodItem foodItem) {
        FoodItem saved = foodService.saveFoodItem(foodItem);
        return ResponseEntity.ok(saved);
    }

    /**
     * Purpose: Updates an existing menu item by ID.
     * Input:   id       — the FoodItem ID.
     *          foodItem — updated fields in the request body.
     * Output:  The updated FoodItem entity.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BILLER')")
    public ResponseEntity<FoodItem> updateFoodItem(@PathVariable Long id, @RequestBody FoodItem foodItem) {
        foodItem.setId(id); // ensure the correct ID is set for the update
        FoodItem saved = foodService.saveFoodItem(foodItem);
        return ResponseEntity.ok(saved);
    }

    /**
     * Purpose: Deletes a menu item by ID.
     * Input:   id — the FoodItem ID to delete.
     * Output:  HTTP 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BILLER')")
    public ResponseEntity<Void> deleteFoodItem(@PathVariable Long id) {
        foodService.deleteFoodItem(id);
        return ResponseEntity.noContent().build();
    }
}
