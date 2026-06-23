package com.serveflow.mapper;

import com.serveflow.dto.response.FoodItemResponseDTO;
import com.serveflow.entity.FoodItem;
import org.springframework.stereotype.Component;

/**
 * FoodItemMapper — converts between FoodItem entities and FoodItemResponseDTOs.
 * Manual field-by-field mapping for explicitness and learnability.
 */
@Component
public class FoodItemMapper {

    /**
     * Purpose: Converts a FoodItem entity to a FoodItemResponseDTO.
     * Input:   foodItem — the FoodItem entity from the database.
     * Output:  FoodItemResponseDTO safe to send to the frontend.
     */
    public FoodItemResponseDTO toDTO(FoodItem foodItem) {
        if (foodItem == null) {
            return null;
        }

        FoodItemResponseDTO dto = new FoodItemResponseDTO();
        dto.setId(foodItem.getId());
        dto.setName(foodItem.getName());
        dto.setCategory(foodItem.getCategory());
        dto.setPrice(foodItem.getPrice());
        dto.setImageUrl(foodItem.getImageUrl());
        dto.setQuantityAvailable(foodItem.getQuantityAvailable());
        dto.setIsVeg(foodItem.getIsVeg());
        dto.setDescription(foodItem.getDescription());

        return dto;
    }
}
