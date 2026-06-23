package com.serveflow.mapper;

import com.serveflow.dto.response.BillResponseDTO;
import com.serveflow.entity.Bill;
import com.serveflow.entity.Payment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BillMapper — converts between Bill entities and BillResponseDTOs.
 *
 * Manual field-by-field mapping is used deliberately (no MapStruct).
 * This keeps the mapping code explicit and readable — every field
 * assignment is visible and can be debugged line by line.
 */
@Component
public class BillMapper {

    private final PaymentMapper paymentMapper;
    private final TokenMapper tokenMapper;

    public BillMapper(PaymentMapper paymentMapper, TokenMapper tokenMapper) {
        this.paymentMapper = paymentMapper;
        this.tokenMapper = tokenMapper;
    }

    /**
     * Purpose: Converts a Bill entity to a BillResponseDTO for the frontend.
     * Input:   bill — the Bill entity from the database.
     * Output:  A BillResponseDTO safe to send to the frontend (no circular references).
     */
    public BillResponseDTO toDTO(Bill bill) {
        if (bill == null) {
            return null;
        }

        BillResponseDTO dto = new BillResponseDTO();
        dto.setId(bill.getId());
        dto.setItemName(bill.getItemName());
        dto.setQuantity(bill.getQuantity());
        dto.setUnitRate(bill.getUnitRate());
        dto.setAmount(bill.getAmount());
        dto.setPaymentMode(bill.getPaymentMode());
        dto.setStatus(bill.getStatus());
        dto.setCreatedAt(bill.getCreatedAt());

        // Map the token only if one was generated.
        if (bill.getToken() != null) {
            dto.setToken(tokenMapper.toDTO(bill.getToken()));
        }

        return dto;
    }

    /**
     * Purpose: Converts a Bill entity to a BillResponseDTO and populates candidatePayments.
     *          Used specifically for AMBIGUOUS bills where we need to show candidate payments.
     * Input:   bill             — the AMBIGUOUS bill entity.
     *          candidatePayments — the list of unmatched payments of the same amount.
     * Output:  BillResponseDTO with candidatePayments populated.
     */
    public BillResponseDTO toDTOWithCandidates(Bill bill, List<Payment> candidatePayments) {
        BillResponseDTO dto = toDTO(bill);

        // Build the candidate payment list for the biller's disambiguation panel.
        List<BillResponseDTO.CandidatePaymentDTO> candidates = new ArrayList<>();
        for (Payment payment : candidatePayments) {
            BillResponseDTO.CandidatePaymentDTO candidate = new BillResponseDTO.CandidatePaymentDTO();
            candidate.setPaymentId(payment.getId());
            candidate.setAmount(payment.getAmount());
            candidate.setReceivedAt(payment.getReceivedAt());

            // Extract the last 4 characters of the UPI reference ID.
            // If the reference ID is shorter than 4 chars (shouldn't happen in real data),
            // just use the full ID.
            String refId = payment.getUpiReferenceId();
            if (refId != null && refId.length() >= 4) {
                candidate.setLast4Digits(refId.substring(refId.length() - 4));
            } else {
                candidate.setLast4Digits(refId);
            }

            candidates.add(candidate);
        }
        dto.setCandidatePayments(candidates);

        return dto;
    }
}
