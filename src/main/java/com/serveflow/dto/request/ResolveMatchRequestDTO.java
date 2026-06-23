package com.serveflow.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ResolveMatchRequestDTO — carries the biller's manual disambiguation selection.
 *
 * Used as the request body for POST /api/biller/resolve-match.
 * Called when a bill is in AMBIGUOUS status and the biller manually clicks
 * the correct candidate payment from the MULTIPLE MATCH panel in QuickBill.
 *
 * Fields:
 *   billId          — the ID of the AMBIGUOUS bill to resolve.
 *   chosenPaymentId — the ID of the Payment the biller selected as the correct match.
 */
@Data
public class ResolveMatchRequestDTO {

    @NotNull(message = "Bill ID is required")
    private Long billId;

    @NotNull(message = "Chosen payment ID is required")
    private Long chosenPaymentId;
}
