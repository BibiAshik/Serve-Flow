package com.serveflow.dto.response;

import lombok.Data;

/**
 * FoodItemResponseDTO — carries menu item details to both portals.
 *
 * For Campus Bite: used to render the student menu grid (name, price, image, category, veg/non-veg).
 * For QuickBill:   used to populate the biller's item-selection dropdown.
 *                  The dropdown needs: id (to submit), name (to display), price (to auto-fill unit rate).
 */
@Data
public class FoodItemResponseDTO {

    private Long id;
    private String name;
    private String category;
    private Double price;
    private String imageUrl;
    private Integer quantityAvailable;
    private Boolean isVeg;
    private String description;
}
