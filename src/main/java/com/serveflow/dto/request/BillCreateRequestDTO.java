package com.serveflow.dto.request;

import com.serveflow.entity.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * BillCreateRequestDTO — carries the details for a new counter bill from the QuickBill UI.
 *
 * This DTO is the request body for POST /api/biller/bills.
 *
 * Fields:
 *   foodItemId   — optional FK to FoodItem table. If provided, itemName is auto-filled
 *                  from the FoodItem record. Set to null for custom/non-standard items.
 *   itemName     — display name for the bill. Required — must be set either from
 *                  FoodItem.name or typed by the biller.
 *   quantity     — number of units billed. Must be at least 1.
 *   unitRate     — price per unit. Must be positive. Auto-filled from FoodItem.price but editable.
 *   paymentMode  — CASH (immediate token) or UPI (matching engine triggered).
 *
 * Amount is NOT included — the service calculates it as unitRate × quantity.
 */
@Data
public class BillCreateRequestDTO {

    // Optional — FK to FoodItem. If null, the biller typed a custom item name.
    private Long foodItemId;

    @NotBlank(message = "Item name is required")
    private String itemName;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Unit rate is required")
    @DecimalMin(value = "0.01", message = "Unit rate must be greater than zero")
    private BigDecimal unitRate;

    @NotNull(message = "Payment mode is required (CASH or UPI)")
    private PaymentMode paymentMode;
}
