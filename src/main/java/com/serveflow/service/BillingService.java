package com.serveflow.service;

import com.serveflow.dto.response.BillResponseDTO;
import com.serveflow.dto.response.LiveStatusDTO;
import com.serveflow.dto.response.PaymentResponseDTO;
import com.serveflow.dto.response.TokenResponseDTO;
import com.serveflow.entity.*;
import com.serveflow.mapper.BillMapper;
import com.serveflow.mapper.PaymentMapper;
import com.serveflow.mapper.TokenMapper;
import com.serveflow.repository.BillRepository;
import com.serveflow.repository.MatchAttemptLogRepository;
import com.serveflow.repository.PaymentRepository;
import com.serveflow.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * BillingService — handles creation of counter bills and the live-status
 * snapshot.
 *
 * This service is the entry point for the QuickBill billing workflow:
 * 1. Biller selects an item, enters quantity and unit rate, picks payment mode.
 * 2. BillingService.createBill() is called.
 * 3. If CASH: token generated immediately, status = CASH_CONFIRMED.
 * 4. If UPI: bill saved as WAITING_PAYMENT,
 * PaymentMatchingService.attemptMatch() called.
 *
 * getLiveBillingStatus() is called every 2.5 seconds by billing.js to refresh
 * the
 * entire QuickBill screen (pending bills, ambiguous bills, recent tokens,
 * payments, stats).
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final TokenRepository tokenRepository;
    private final MatchAttemptLogRepository matchAttemptLogRepository;
    private final PaymentMatchingService paymentMatchingService;
    private final TokenService tokenService;
    private final PrinterService printerService;
    private final BillMapper billMapper;
    private final PaymentMapper paymentMapper;
    private final TokenMapper tokenMapper;

    @Value("${app.matching-window-minutes:10}")
    private int matchingWindowMinutes;

    public void setMatchingWindowMinutes(int matchingWindowMinutes) {
        this.matchingWindowMinutes = matchingWindowMinutes;
    }

    public BillingService(BillRepository billRepository,
            PaymentRepository paymentRepository,
            TokenRepository tokenRepository,
            MatchAttemptLogRepository matchAttemptLogRepository,
            PaymentMatchingService paymentMatchingService,
            TokenService tokenService,
            PrinterService printerService,
            BillMapper billMapper,
            PaymentMapper paymentMapper,
            TokenMapper tokenMapper) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.tokenRepository = tokenRepository;
        this.matchAttemptLogRepository = matchAttemptLogRepository;
        this.paymentMatchingService = paymentMatchingService;
        this.tokenService = tokenService;
        this.printerService = printerService;
        this.billMapper = billMapper;
        this.paymentMapper = paymentMapper;
        this.tokenMapper = tokenMapper;
    }

    /**
     * Purpose: Creates a new counter bill for a walk-in customer.
     * CASH mode: token generated immediately (no payment matching needed).
     * UPI mode: bill saved as WAITING_PAYMENT; matching engine invoked.
     * Input: itemName, quantity, unitRate, paymentMode, foodItem (optional FK).
     * Output: BillResponseDTO with the created bill's status and token (if
     * generated).
     */
    @Transactional
    public BillResponseDTO createBill(String itemName, Integer quantity, BigDecimal unitRate,
            PaymentMode paymentMode, FoodItem foodItem) {

        // Step 1: Calculate the total amount = unit rate × quantity.
        BigDecimal amount = unitRate.multiply(BigDecimal.valueOf(quantity));

        // Step 2: Create and populate the Bill entity.
        Bill bill = new Bill();
        bill.setFoodItem(foodItem); // may be null if biller typed a custom item
        bill.setItemName(itemName);
        bill.setQuantity(quantity);
        bill.setUnitRate(unitRate);
        bill.setAmount(amount);
        bill.setPaymentMode(paymentMode);
        bill.setCreatedAt(LocalDateTime.now());

        if (paymentMode == PaymentMode.CASH) {
            // ── CASH PAYMENT: token generated immediately ─────────────────────────────
            // No UPI, no matching engine. Bill is confirmed right away.
            bill.setStatus(BillStatus.CASH_CONFIRMED);
            Bill savedBill = billRepository.save(bill);

            // Generate a token immediately for this cash bill.
            Token token = tokenService.generateToken(savedBill);

            // Link the token back to the bill.
            savedBill.setToken(token);
            savedBill = billRepository.save(savedBill);

            log.info("createBill: CASH bill {} confirmed. Token #{} generated.", savedBill.getId(),
                    token.getTokenNumber());
            return billMapper.toDTO(savedBill);

        } else {
            // ── UPI PAYMENT: save as WAITING, then attempt immediate match ───────────
            // The matching engine might find an already-arrived unmatched payment
            // immediately.
            bill.setStatus(BillStatus.WAITING_PAYMENT);
            Bill savedBill = billRepository.save(bill);

            log.info("createBill: UPI bill {} created. Attempting immediate payment match.", savedBill.getId());

            // Attempt to match this bill against any already-arrived unmatched payments.
            // This handles the case where the customer paid BEFORE the biller created the
            // bill.
            paymentMatchingService.attemptMatch(savedBill);

            // Reload the bill to get its updated status (may have changed to MATCHED or
            // AMBIGUOUS).
            Bill reloadedBill = billRepository.findById(savedBill.getId()).orElse(savedBill);

            // If it was immediately AMBIGUOUS, populate the candidate payments in the
            // response.
            if (reloadedBill.getStatus() == BillStatus.AMBIGUOUS) {
                LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(matchingWindowMinutes);
                List<Payment> candidates = paymentRepository.findUnmatchedPaymentsByAmountWithinWindow(
                        reloadedBill.getAmount(), cutoffTime);
                return billMapper.toDTOWithCandidates(reloadedBill, candidates);
            }

            return billMapper.toDTO(reloadedBill);
        }
    }

    /**
     * Purpose: Builds the complete live-status snapshot for the QuickBill billing
     * screen.
     * Called every 2.5 seconds by billing.js via GET /api/biller/live-status.
     * Input: none.
     * Output: LiveStatusDTO containing all zones needed to re-render the QuickBill
     * UI.
     */
    public LiveStatusDTO getLiveBillingStatus() {
        LiveStatusDTO liveStatus = new LiveStatusDTO();

        // ── PENDING BILLS (WAITING_PAYMENT) ──────────────────────────────────────────
        List<Bill> pendingBills = billRepository.findByStatusOrderByCreatedAtAsc(BillStatus.WAITING_PAYMENT);
        List<BillResponseDTO> pendingDTOs = new ArrayList<>();
        for (Bill bill : pendingBills) {
            pendingDTOs.add(billMapper.toDTO(bill));
        }
        liveStatus.setPendingBills(pendingDTOs);

        // ── AMBIGUOUS BILLS with candidate payments
        // ───────────────────────────────────
        List<Bill> ambiguousBills = billRepository.findByStatusOrderByCreatedAtAsc(BillStatus.AMBIGUOUS);
        List<BillResponseDTO> ambiguousDTOs = new ArrayList<>();
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(matchingWindowMinutes);

        for (Bill bill : ambiguousBills) {
            // Load the candidate payments for this ambiguous bill.
            List<Payment> candidates = paymentRepository.findUnmatchedPaymentsByAmountWithinWindow(
                    bill.getAmount(), cutoffTime);
            ambiguousDTOs.add(billMapper.toDTOWithCandidates(bill, candidates));
        }
        liveStatus.setAmbiguousBills(ambiguousDTOs);

        // ── RECENT TOKENS (last 3)
        // ────────────────────────────────────────────────────
        List<Token> allTokens = tokenRepository.findAllByOrderByGeneratedAtDesc();
        List<TokenResponseDTO> recentTokenDTOs = new ArrayList<>();
        int tokenCount = Math.min(3, allTokens.size()); // show only last 3
        for (int i = 0; i < tokenCount; i++) {
            Token token = allTokens.get(i);
            TokenResponseDTO tokenDTO = tokenMapper.toDTO(token);

            // If the token failed to print, generate virtual print HTML for on-screen
            // display.
            if (token.getStatus() == TokenStatus.PRINT_FAILED) {
                tokenDTO.setVirtualPrintHtml(buildVirtualPrintHtml(token));
            }

            recentTokenDTOs.add(tokenDTO);
        }
        liveStatus.setRecentTokens(recentTokenDTOs);

        // ── RECENT PAYMENTS
        // ───────────────────────────────────────────────────────────
        // 1. Fetch ALL unmatched payments from the last 12 hours (so they never disappear early)
        LocalDateTime twelveHoursAgo = LocalDateTime.now().minusHours(12);
        List<Payment> unmatchedPayments = paymentRepository.findByStatusAndReceivedAtAfterOrderByReceivedAtDesc(
                UpiPaymentStatus.UNMATCHED, twelveHoursAgo);

        // 2. Fetch the top 10 most recent MATCHED payments (for history visibility, also within 12 hours)
        List<Payment> matchedPayments = paymentRepository.findTop10ByStatusAndReceivedAtAfterOrderByReceivedAtDesc(
                UpiPaymentStatus.MATCHED, twelveHoursAgo);

        // 3. Combine them into one list
        List<Payment> combinedPayments = new ArrayList<>();
        combinedPayments.addAll(unmatchedPayments);
        combinedPayments.addAll(matchedPayments);

        // 4. Sort the combined list by receivedAt DESC (newest first)
        combinedPayments.sort((p1, p2) -> p2.getReceivedAt().compareTo(p1.getReceivedAt()));

        // 5. If the combined list is huge, we can optionally cap it.
        // But since unmatched is strictly bounded by 12 hours and matched is capped at 10,
        // it shouldn't be too large. We'll cap the total displayed at 30 to avoid UI lag.
        int paymentLimit = Math.min(30, combinedPayments.size());

        List<PaymentResponseDTO> recentPaymentDTOs = new ArrayList<>();
        for (int i = 0; i < paymentLimit; i++) {
            recentPaymentDTOs.add(paymentMapper.toDTO(combinedPayments.get(i)));
        }
        liveStatus.setRecentPayments(recentPaymentDTOs);

        // ── BOTTOM STATUS BAR COUNTS
        // ──────────────────────────────────────────────────
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        liveStatus.setTotalBillsToday(billRepository.countBillsCreatedAfter(startOfDay));
        liveStatus.setTotalPaymentsToday(
                paymentRepository.countByStatusAndReceivedAtAfter(UpiPaymentStatus.UNMATCHED, startOfDay) +
                        paymentRepository.countByStatusAndReceivedAtAfter(UpiPaymentStatus.MATCHED, startOfDay));
        liveStatus.setUnmatchedPaymentCount(
                paymentRepository.countByStatusAndReceivedAtAfter(UpiPaymentStatus.UNMATCHED, startOfDay));
        liveStatus.setMatchedCount(
                paymentRepository.countByStatusAndReceivedAtAfter(UpiPaymentStatus.MATCHED, startOfDay));

        // ── PRINTER STATUS
        // ────────────────────────────────────────────────────────────
        liveStatus.setPrinterStatus(printerService.getPrinterStatus());

        return liveStatus;
    }

    /**
     * Purpose: Runs every 1 minute to check for bills that have been sitting in
     * WAITING_PAYMENT or AMBIGUOUS status for more than the matching window (10 minutes).
     * It marks them as CANCELLED so they disappear from the billing screen and
     * stop cluttering the UI or matching engine.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    @org.springframework.transaction.annotation.Transactional
    public void cancelExpiredBills() {
        // Bills older than the matching window
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(matchingWindowMinutes);
        List<BillStatus> statuses = List.of(BillStatus.WAITING_PAYMENT, BillStatus.AMBIGUOUS);

        List<Bill> expiredBills = billRepository.findByStatusInAndCreatedAtBefore(statuses, cutoff);

        if (!expiredBills.isEmpty()) {
            log.info("cancelExpiredBills: Found {} abandoned bills older than {} minutes. Cancelling them.",
                    expiredBills.size(), matchingWindowMinutes);
            for (Bill bill : expiredBills) {
                bill.setStatus(BillStatus.CANCELLED);
            }
            billRepository.saveAll(expiredBills);
        }
    }

    /**
     * Purpose: Builds an HTML snippet for virtual (on-screen) token display when
     * the physical printer is offline.
     * Input: token — the token to render as HTML.
     * Output: An HTML string that billing.js injects into the Recent Tokens zone.
     * The "Print" button uses window.print() to print this div via the browser.
     */
    private String buildVirtualPrintHtml(Token token) {
        String formattedTime = token.getGeneratedAt() != null
                ? token.getGeneratedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "N/A";

        return "<div class='virtual-token-slip'>" +
                "<div class='token-header'>CAMPUS BITE CANTEEN</div>" +
                "<div class='token-number'>TOKEN #" + token.getTokenNumber() + "</div>" +
                "<div class='token-item'>" + token.getItemSummary() + "</div>" +
                "<div class='token-amount'>₹" + token.getAmount() + "</div>" +
                "<div class='token-time'>" + formattedTime + "</div>" +
                "<button onclick='printVirtualToken(this)' class='btn-print'>🖨 Print</button>" +
                "</div>";
    }
}
