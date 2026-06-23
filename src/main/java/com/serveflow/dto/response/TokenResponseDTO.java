package com.serveflow.dto.response;

import com.serveflow.entity.TokenStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TokenResponseDTO — carries token details to both the biller (QuickBill) and student (Campus Bite).
 *
 * For QuickBill: shown in the "Recent Tokens" zone and used for virtual print rendering.
 * For Campus Bite: shown on the My Orders page as the student's food pickup token.
 *
 * The virtualPrintHtml field is only populated when the printer was offline —
 * billing.js renders this as a styled printable div when present.
 */
@Data
public class TokenResponseDTO {

    private Long id;
    private Long tokenNumber;     // The sequential number displayed to the customer
    private String itemSummary;   // e.g. "Chicken Fried Rice x2"
    private BigDecimal amount;
    private TokenStatus status;   // ACTIVE, PRINTED, or PRINT_FAILED
    private LocalDateTime generatedAt;
    private LocalDateTime printedAt; // null if not yet printed

    // Source type — helps the frontend know which detail to show.
    // "COUNTER" for Flow B (QuickBill counter bills), "ONLINE" for Flow A (Campus Bite orders).
    private String sourceType;

    // HTML snippet for virtual printing — only set when status = PRINT_FAILED.
    // billing.js injects this into a styled div in the Recent Tokens zone.
    // Students see their token as a styled card on my-orders.html instead.
    private String virtualPrintHtml;
}
