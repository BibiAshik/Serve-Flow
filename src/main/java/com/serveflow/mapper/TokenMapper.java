package com.serveflow.mapper;

import com.serveflow.dto.response.TokenResponseDTO;
import com.serveflow.entity.Token;
import org.springframework.stereotype.Component;

/**
 * TokenMapper — converts between Token entities and TokenResponseDTOs.
 * Manual field-by-field mapping for explicitness and learnability.
 */
@Component
public class TokenMapper {

    /**
     * Purpose: Converts a Token entity to a TokenResponseDTO for the frontend.
     * Input:   token — the Token entity.
     * Output:  TokenResponseDTO. sourceType is "COUNTER" for bill-based tokens,
     *          "ONLINE" for order-based tokens. virtualPrintHtml is null by default
     *          (set by PrinterService when the printer is offline).
     */
    public TokenResponseDTO toDTO(Token token) {
        if (token == null) {
            return null;
        }

        TokenResponseDTO dto = new TokenResponseDTO();
        dto.setId(token.getId());
        dto.setTokenNumber(token.getTokenNumber());
        dto.setItemSummary(token.getItemSummary());
        dto.setAmount(token.getAmount());
        dto.setStatus(token.getStatus());
        dto.setGeneratedAt(token.getGeneratedAt());
        dto.setPrintedAt(token.getPrintedAt());

        // Determine the source type based on which flow generated this token.
        // Exactly one of bill or order will be non-null.
        if (token.getBill() != null) {
            dto.setSourceType("COUNTER"); // Flow B — QuickBill counter bill
        } else {
            dto.setSourceType("ONLINE");  // Flow A — Campus Bite online order
        }

        // virtualPrintHtml is set by PrinterService.virtualPrintFallback() when the
        // physical printer is offline. Null here — service layer populates it if needed.
        dto.setVirtualPrintHtml(null);

        return dto;
    }
}
