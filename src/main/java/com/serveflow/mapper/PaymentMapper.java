package com.serveflow.mapper;

import com.serveflow.dto.response.PaymentResponseDTO;
import com.serveflow.entity.Payment;
import org.springframework.stereotype.Component;

/**
 * PaymentMapper — converts between Payment entities and PaymentResponseDTOs.
 * Manual field-by-field mapping for explicitness and learnability.
 */
@Component
public class PaymentMapper {

    /**
     * Purpose: Converts a Payment entity to a PaymentResponseDTO.
     * Input:   payment — the Payment entity.
     * Output:  PaymentResponseDTO with last4Digits derived from upiReferenceId.
     */
    public PaymentResponseDTO toDTO(Payment payment) {
        if (payment == null) {
            return null;
        }

        PaymentResponseDTO dto = new PaymentResponseDTO();
        dto.setId(payment.getId());
        dto.setAmount(payment.getAmount());
        dto.setReceivedAt(payment.getReceivedAt());
        dto.setStatus(payment.getStatus());
        dto.setUpiReferenceId(payment.getUpiReferenceId());

        // Derive last 4 digits — used in the AMBIGUOUS panel for biller disambiguation.
        String refId = payment.getUpiReferenceId();
        if (refId != null && refId.length() >= 4) {
            dto.setLast4Digits(refId.substring(refId.length() - 4));
        } else {
            dto.setLast4Digits(refId);
        }

        return dto;
    }
}
