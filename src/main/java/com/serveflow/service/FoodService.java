package com.serveflow.service;

import com.serveflow.dto.response.FoodItemResponseDTO;
import com.serveflow.entity.FoodItem;
import com.serveflow.mapper.FoodItemMapper;
import com.serveflow.repository.FoodItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * FoodService — manages the food menu for both portals.
 *
 * Used by:
 * - Campus Bite: student menu browsing and item detail display.
 * - QuickBill: biller's item-selection dropdown and Quick Items buttons.
 * - Admin CRUD: adding, editing, and removing menu items.
 */
@Service
public class FoodService {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemMapper foodItemMapper;

    public FoodService(FoodItemRepository foodItemRepository, FoodItemMapper foodItemMapper) {
        this.foodItemRepository = foodItemRepository;
        this.foodItemMapper = foodItemMapper;
    }

    /**
     * Purpose: Returns all food items as entities (used internally by other
     * services).
     * Input: none.
     * Output: List of all FoodItem entities from the database.
     */
    public List<FoodItem> getAllFoodItems() {
        return foodItemRepository.findAll();
    }

    /**
     * Purpose: Returns all food items as DTOs for the student menu page.
     * Input: none.
     * Output: List of FoodItemResponseDTO — safe to send to the frontend.
     */
    public List<FoodItemResponseDTO> getAllFoodItemDTOs() {
        List<FoodItem> items = foodItemRepository.findAll();
        List<FoodItemResponseDTO> dtos = new ArrayList<>();
        for (FoodItem item : items) {
            dtos.add(foodItemMapper.toDTO(item));
        }
        return dtos;
    }

    /**
     * Purpose: Returns all food items formatted for the biller's item-selection
     * dropdown.
     * The biller selects from this list when creating a counter bill — they NEVER
     * type item names manually (to prevent naming inconsistencies).
     * Input: none.
     * Output: List of FoodItemResponseDTO (id, name, price are most important for
     * the dropdown).
     */
    public List<FoodItemResponseDTO> getAllFoodItemsForDropdown() {
        return getAllFoodItemDTOs();
    }

    /**
     * Purpose: Saves a food item to the database (create or update).
     * Input: foodItem — the FoodItem entity to save. If id is null, creates new. If
     * id exists, updates.
     * Output: the saved FoodItem entity.
     */
    public FoodItem saveFoodItem(FoodItem foodItem) {
        return foodItemRepository.save(foodItem);
    }

    /**
     * Purpose: Deletes a food item from the menu by its ID.
     * Input: id — the ID of the FoodItem to delete.
     * Output: void.
     */
    public void deleteFoodItem(Long id) {
        foodItemRepository.deleteById(id);
    }

    /**
     * Purpose: Fetches a single food item by its ID.
     * Input: id — the FoodItem ID to look up.
     * Output: the FoodItem entity, or null if not found.
     */
    public FoodItem getFoodItemById(Long id) {
        return foodItemRepository.findById(id).orElse(null);
    }
}
