package com.serveflow.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * OnlineOrderCreateRequestDTO — carries a student's online pre-order from Campus Bite checkout.
 *
 * Used as the request body for POST /api/online/orders.
 * The student's email is NOT included here — it is extracted from the JWT by
 * OnlineOrderController using @AuthenticationPrincipal. This prevents students
 * from submitting orders on behalf of other students.
 */
@Data
public class OnlineOrderCreateRequestDTO {

    // List of items the student is ordering.
    @NotNull(message = "Order must contain at least one item")
    private List<OrderItemDTO> items;

    // Preferred pickup time (e.g. "12:30 PM") — informational only.
    private String pickupTime;

    /**
     * OrderItemDTO — a single item in the student's cart.
     * Nested inside the request to avoid creating a separate file for a simple pair.
     */
    @Data
    public static class OrderItemDTO {

        @NotNull(message = "Food item ID is required")
        private Long foodItemId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}
