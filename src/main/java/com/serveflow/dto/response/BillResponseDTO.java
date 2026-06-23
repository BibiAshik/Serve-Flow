package com.serveflow.dto.response;

import com.serveflow.entity.BillStatus;
import com.serveflow.entity.PaymentMode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BillResponseDTO — carries bill details from the backend to the QuickBill frontend.
 *
 * Returned by POST /api/biller/bills and included in the LiveStatusDTO.
 * If a token was generated immediately (cash or immediate auto-match),
 * the token field will be populated. Otherwise it is null.
 *
 * candidatePayments is only populated when status = AMBIGUOUS.
 */
@Data
public class BillResponseDTO {

    private Long id;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitRate;
    private BigDecimal amount;
    private PaymentMode paymentMode;
    private BillStatus status;
    private LocalDateTime createdAt;

    // The generated token — non-null only when status is MATCHED or CASH_CONFIRMED.
    private TokenResponseDTO token;

    // Candidate payments for biller selection — non-null only when status = AMBIGUOUS.
    // Each entry shows the timestamp and last 4 chars of the UPI reference ID.
    private List<CandidatePaymentDTO> candidatePayments;

    /**
     * CandidatePaymentDTO — a single candidate payment shown in the AMBIGUOUS panel.
     * The biller sees these and clicks the one that matches what the customer shows them.
     */
    @Data
    public static class CandidatePaymentDTO {
        private Long paymentId;
        private BigDecimal amount;
        private LocalDateTime receivedAt;
        // Last 4 characters of the UPI reference ID — shown to help biller identify payment.
        // Customer reads out their reference number and biller types/compares these 4 chars.
        private String last4Digits;
    }
}
