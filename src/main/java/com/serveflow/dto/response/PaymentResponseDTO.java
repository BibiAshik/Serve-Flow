package com.serveflow.dto.response;

import com.serveflow.entity.UpiPaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentResponseDTO — carries UPI payment details to the QuickBill frontend.
 *
 * Included in the LiveStatusDTO's recentPayments list and used in the
 * "Payments Received" zone (Column 3) of the billing screen.
 *
 * Note: We expose the last 4 characters of the UPI reference ID (not the full ID)
 * for the AMBIGUOUS panel. The biller asks the customer to read out these 4 chars.
 */
@Data
public class PaymentResponseDTO {

    private Long id;
    private BigDecimal amount;
    private LocalDateTime receivedAt;
    private UpiPaymentStatus status; // UNMATCHED or MATCHED
    // Last 4 characters of the UPI reference — shown in the ambiguous panel.
    private String last4Digits;
    // Full reference ID shown in transaction history (for audit purposes).
    private String upiReferenceId;
}
