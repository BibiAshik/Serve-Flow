package com.serveflow.dto.response;

import lombok.Data;

import java.util.List;

/**
 * LiveStatusDTO — the complete snapshot returned by GET /api/biller/live-status.
 *
 * This is the most important response DTO in the system. It is fetched by billing.js
 * every 2500ms and used to re-render the entire QuickBill billing screen.
 *
 * Contains:
 *   pendingBills      — bills in WAITING_PAYMENT state (Column 2, top zone)
 *   ambiguousBills    — bills in AMBIGUOUS state with candidate payments (Column 2, bottom zone)
 *   recentTokens      — last 3 generated tokens (Column 3, bottom zone)
 *   recentPayments    — recent incoming UPI payments (Column 3, top zone)
 *   totalBillsToday   — counter for the bottom status bar
 *   totalPaymentsToday— counter for the bottom status bar
 *   matchedCount      — how many bills have been successfully matched today
 *   unmatchedPaymentCount — how many payments are still unmatched
 *   printerStatus     — "ONLINE" or "OFFLINE" (bottom status bar printer indicator)
 */
@Data
public class LiveStatusDTO {

    // Bills waiting for a UPI payment to arrive — shown in the "Waiting for Payment" zone.
    private List<BillResponseDTO> pendingBills;

    // Bills in AMBIGUOUS state — shown in the "Multiple Match Found" panel (red/pink).
    // Each bill includes candidatePayments for the biller to choose from.
    private List<BillResponseDTO> ambiguousBills;

    // The 3 most recently generated tokens — shown in "Recent Tokens" zone.
    private List<TokenResponseDTO> recentTokens;

    // Recent incoming payments — shown in "Payments Received" zone.
    private List<PaymentResponseDTO> recentPayments;

    // Bottom status bar counts.
    private long totalBillsToday;
    private long totalPaymentsToday;
    private long matchedCount;
    private long unmatchedPaymentCount;

    // Printer status — "ONLINE" (green dot) or "OFFLINE" (red dot).
    private String printerStatus;
}
