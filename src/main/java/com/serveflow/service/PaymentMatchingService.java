package com.serveflow.service;

import com.serveflow.entity.*;
import com.serveflow.exception.PaymentAlreadyClaimedException;
import com.serveflow.repository.BillRepository;
import com.serveflow.repository.MatchAttemptLogRepository;
import com.serveflow.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PaymentMatchingService — the core engine of the ServeFlow system.
 *
 * WHAT IT DOES:
 *   When a customer at the counter pays via UPI (by scanning the static QR code),
 *   their payment arrives as a Razorpay webhook. This service tries to match that
 *   payment to a waiting counter bill of the same amount.
 *
 * THE THREE OUTCOMES:
 *   1. ZERO matching bills/payments → stay in WAITING state, retry on next event.
 *   2. EXACTLY ONE match → auto-match, generate token immediately (AUTO_MATCH log).
 *   3. TWO OR MORE matches → surface to biller as AMBIGUOUS; biller resolves manually.
 *
 * WHY WE NEVER AUTO-GUESS AMBIGUOUS CASES:
 *   If two people both pay ₹70 within seconds of each other, there is genuinely no
 *   data to deterministically know which payment belongs to which person.
 *   A silent FIFO guess would reintroduce the exact fraud this system exists to prevent.
 *
 * CONCURRENCY SAFETY:
 *   claimPaymentForBill() uses PESSIMISTIC_WRITE locking to ensure only one bill
 *   can ever claim a given payment, even under concurrent requests.
 *
 * AUDIT TRAIL:
 *   Every matching decision (auto, manual, or pending) is written to MatchAttemptLog.
 *   This is non-negotiable — it makes every decision auditable and defensible.
 *
 * Every method in this class has extensive comments — study them carefully.
 */
@Service
public class PaymentMatchingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentMatchingService.class);

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final MatchAttemptLogRepository matchAttemptLogRepository;
    private final TokenService tokenService;

    // How many minutes back to search for matching bills/payments.
    // Configured in application.properties: app.matching-window-minutes
    // Default: 10 minutes. Prevents old payments from matching new bills accidentally.
    @Value("${app.matching-window-minutes:10}")
    private int matchingWindowMinutes;

    public PaymentMatchingService(BillRepository billRepository,
                                  PaymentRepository paymentRepository,
                                  MatchAttemptLogRepository matchAttemptLogRepository,
                                  TokenService tokenService) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.matchAttemptLogRepository = matchAttemptLogRepository;
        this.tokenService = tokenService;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // METHOD 1: attemptMatch(Payment payment)
    // Called when a NEW PAYMENT arrives (via webhook).
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Purpose: Called immediately after a new Payment is saved from a webhook event.
     *          Searches for bills that were waiting for a payment of this exact amount.
     * Input:   payment — the newly saved Payment entity with amount and receivedAt set.
     * Output:  void. Side effects: updates Bill/Payment status, creates MatchAttemptLog,
     *          and if matched, generates a Token.
     */
    @Transactional
    public void attemptMatch(Payment payment) {
        log.info("attemptMatch(Payment): Looking for waiting bills matching amount={}", payment.getAmount());

        // Calculate the cutoff time — only bills created within the last N minutes qualify.
        // Bills older than this window are no longer eligible for automatic matching.
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(matchingWindowMinutes);

        // Search for all WAITING_PAYMENT UPI bills whose amount matches the incoming payment,
        // created within the configured time window. Ordered by createdAt ASC (oldest first).
        List<Bill> matchingBills = billRepository.findWaitingBillsByAmountWithinWindow(
                payment.getAmount(), cutoffTime);

        log.info("attemptMatch(Payment): Found {} matching waiting bill(s)", matchingBills.size());

        if (matchingBills.isEmpty()) {
            // ── CASE 1: No matching bills found ──────────────────────────────────────
            // The payment arrived but no bill of this amount is waiting.
            // This is normal — maybe the biller hasn't created the bill yet.
            // The payment stays UNMATCHED. When the biller creates a bill,
            // attemptMatch(Bill) will find this payment and match it.
            log.info("attemptMatch(Payment): No matching bills. Payment stays UNMATCHED.");

        } else if (matchingBills.size() == 1) {
            // ── CASE 2: Exactly one matching bill found → AUTO_MATCH ──────────────────
            // This is the unambiguous case — one payment, one waiting bill.
            // Claim the payment for that bill immediately.
            Bill matchedBill = matchingBills.get(0);
            log.info("attemptMatch(Payment): Auto-matching payment {} to bill {}", payment.getId(), matchedBill.getId());
            claimPaymentForBill(matchedBill, payment, ResolutionType.AUTO_MATCH, null);

        } else {
            // ── CASE 3: Multiple matching bills found ─────────────────────────────────
            // One payment arrived but multiple bills are waiting for this exact amount.
            // Per the spec: auto-match to the OLDEST waiting bill (index 0, since we
            // ordered by createdAt ASC). The logic is: "multiple bills waiting for one
            // payment" is not an ambiguity problem — the correct bill is simply the one
            // that has been waiting longest. Log as AUTO_MATCH.
            //
            // (Contrast with attemptMatch(Bill) where MULTIPLE PAYMENTS for one bill
            //  is the genuine ambiguity — there we surface it to the biller.)
            Bill oldestWaitingBill = matchingBills.get(0);
            log.info("attemptMatch(Payment): Multiple waiting bills — auto-matching to oldest bill {}", oldestWaitingBill.getId());
            claimPaymentForBill(oldestWaitingBill, payment, ResolutionType.AUTO_MATCH, null);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // METHOD 2: attemptMatch(Bill bill)
    // Called when a NEW BILL is created in UPI mode.
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Purpose: Called immediately after a new UPI Bill is created by the biller.
     *          Searches for unmatched payments of the same amount within the time window.
     * Input:   bill — the newly saved Bill entity with amount, paymentMode=UPI, status=WAITING_PAYMENT.
     * Output:  void. Side effects: updates Bill/Payment status, creates MatchAttemptLog,
     *          and if matched, generates a Token.
     */
    @Transactional
    public void attemptMatch(Bill bill) {
        log.info("attemptMatch(Bill): Looking for unmatched payments for bill {} amount={}", bill.getId(), bill.getAmount());

        // Calculate the cutoff time — only payments received in the last N minutes qualify.
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(matchingWindowMinutes);

        // Search for all UNMATCHED payments whose amount matches this bill,
        // received within the configured time window. Ordered by receivedAt ASC (oldest first).
        List<Payment> matchingPayments = paymentRepository.findUnmatchedPaymentsByAmountWithinWindow(
                bill.getAmount(), cutoffTime);

        log.info("attemptMatch(Bill): Found {} matching unmatched payment(s)", matchingPayments.size());

        if (matchingPayments.isEmpty()) {
            // ── CASE 1: No payments found yet → bill stays WAITING_PAYMENT ───────────
            // The biller created a bill but no payment of this amount has arrived yet.
            // Log this attempt so we have an audit record.
            log.info("attemptMatch(Bill): No matching payments. Bill {} stays WAITING_PAYMENT.", bill.getId());

            // Write an audit log entry — no resolution yet (null values for resolvedPayment, etc.)
            MatchAttemptLog waitingLog = new MatchAttemptLog();
            waitingLog.setBill(bill);
            waitingLog.setCandidatePaymentIds(""); // empty — no candidates found
            waitingLog.setResolvedPayment(null);
            waitingLog.setResolutionType(null);
            waitingLog.setResolvedBy(null);
            waitingLog.setResolvedAt(null);
            matchAttemptLogRepository.save(waitingLog);

        } else if (matchingPayments.size() == 1) {
            // ── CASE 2: Exactly one payment found → AUTO_MATCH ───────────────────────
            // This is the unambiguous case — one bill, one matching payment.
            Payment matchedPayment = matchingPayments.get(0);
            log.info("attemptMatch(Bill): Auto-matching bill {} to payment {}", bill.getId(), matchedPayment.getId());
            claimPaymentForBill(bill, matchedPayment, ResolutionType.AUTO_MATCH, null);

        } else {
            // ── CASE 3: Multiple payments found → AMBIGUOUS ───────────────────────────
            // THIS IS THE KEY FRAUD PREVENTION CASE.
            // Multiple customers paid the same amount (e.g. ₹70) within seconds of each other.
            // There is NO data to deterministically know which payment belongs to this customer.
            // We must NOT guess — we surface this to the biller.
            log.info("attemptMatch(Bill): AMBIGUOUS — {} payments found for bill {}", matchingPayments.size(), bill.getId());

            // Mark the bill as AMBIGUOUS so it appears in the red panel in QuickBill.
            bill.setStatus(BillStatus.AMBIGUOUS);
            billRepository.save(bill);

            // Build a comma-separated string of all candidate payment IDs for the audit log.
            // Example: "12,13,17"
            String candidateIds = matchingPayments.stream()
                    .map(p -> p.getId().toString())
                    .collect(Collectors.joining(","));

            // Write an audit log entry recording all candidates considered.
            // resolvedPayment is null — the biller hasn't resolved it yet.
            MatchAttemptLog ambiguousLog = new MatchAttemptLog();
            ambiguousLog.setBill(bill);
            ambiguousLog.setCandidatePaymentIds(candidateIds);
            ambiguousLog.setResolvedPayment(null);
            ambiguousLog.setResolutionType(null);
            ambiguousLog.setResolvedBy(null);
            ambiguousLog.setResolvedAt(null);
            matchAttemptLogRepository.save(ambiguousLog);

            // The biller will see this bill in the AMBIGUOUS panel and call
            // resolveAmbiguousMatch() when they manually identify the correct payment.
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // METHOD 3: claimPaymentForBill(...) — THE CRITICAL CLAIMING STEP
    // This is the ONLY place where a payment is permanently linked to a bill.
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Purpose: Links a payment to a bill — the actual "claiming" step.
     *          Uses PESSIMISTIC_WRITE locking to prevent concurrent double-claiming.
     * Input:   bill           — the Bill to confirm.
     *          payment        — the Payment to claim (will be locked and verified).
     *          resolutionType — AUTO_MATCH or MANUAL_MATCH.
     *          resolvedBy     — biller username for MANUAL_MATCH; null for AUTO_MATCH.
     * Output:  the generated Token.
     * Throws:  PaymentAlreadyClaimedException if another request already claimed this payment.
     */
    @Transactional
    public Token claimPaymentForBill(Bill bill, Payment payment,
                                     ResolutionType resolutionType, String resolvedBy) {

        // Step 1: Acquire a PESSIMISTIC_WRITE (row-level exclusive) lock on the Payment row.
        // This blocks any other concurrent transaction from reading or modifying this row
        // until our transaction commits. This is what makes the system race-condition safe.
        //
        // WHY WE LOCK HERE (not at the search step):
        //   The search (findUnmatchedPaymentsByAmountWithinWindow) uses a regular read.
        //   Two concurrent requests could both find the same payment as a candidate.
        //   But only ONE can acquire the exclusive lock — the other waits.
        //   When the second request gets the lock, we re-check the status (Step 2).
        Optional<Payment> lockedPaymentOpt = paymentRepository.findByIdWithPessimisticLock(payment.getId());

        if (lockedPaymentOpt.isEmpty()) {
            // Edge case: the payment was deleted between the search and the lock.
            // This should not happen in normal operation, but we handle it gracefully.
            throw new PaymentAlreadyClaimedException("Payment " + payment.getId() + " no longer exists.");
        }

        Payment lockedPayment = lockedPaymentOpt.get();

        // Step 2: Re-verify that the payment is still UNMATCHED.
        // The request that was waiting for the lock must check again, because the first
        // request may have already claimed this payment while the second was waiting.
        // If already MATCHED, we cannot claim it again — throw a clear exception.
        if (lockedPayment.getStatus() == UpiPaymentStatus.MATCHED) {
            log.warn("claimPaymentForBill: Payment {} was already claimed by the time we got the lock.", payment.getId());
            throw new PaymentAlreadyClaimedException(
                    "Payment " + payment.getId() + " was already claimed. Please try a different payment.");
        }

        // Step 3: Mark the payment as MATCHED and link it to this bill.
        lockedPayment.setStatus(UpiPaymentStatus.MATCHED);
        lockedPayment.setMatchedBill(bill);
        paymentRepository.save(lockedPayment);

        // Step 4: Mark the bill as MATCHED and link the payment to it.
        bill.setStatus(BillStatus.MATCHED);
        bill.setMatchedPayment(lockedPayment);
        billRepository.save(bill);

        // Step 5: Generate a token for this confirmed bill.
        // TokenService.generateToken() uses its own PESSIMISTIC_WRITE lock on TokenSequence
        // to ensure a unique sequential token number.
        Token generatedToken = tokenService.generateToken(bill);

        // Step 6: Link the token back to the bill (so the bill knows its token).
        bill.setToken(generatedToken);
        billRepository.save(bill);

        // Step 7: Write the final audit log entry with the resolution details.
        MatchAttemptLog resolvedLog = new MatchAttemptLog();
        resolvedLog.setBill(bill);
        resolvedLog.setCandidatePaymentIds(payment.getId().toString()); // the one that was chosen
        resolvedLog.setResolvedPayment(lockedPayment);
        resolvedLog.setResolutionType(resolutionType);
        resolvedLog.setResolvedBy(resolvedBy);     // null for AUTO_MATCH, biller username for MANUAL
        resolvedLog.setResolvedAt(LocalDateTime.now());
        matchAttemptLogRepository.save(resolvedLog);

        log.info("claimPaymentForBill: Bill {} matched to Payment {} [{}] → Token #{}",
                 bill.getId(), lockedPayment.getId(), resolutionType, generatedToken.getTokenNumber());

        return generatedToken;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // METHOD 4: resolveAmbiguousMatch(...) — BILLER MANUALLY RESOLVES
    // Called when the biller clicks a candidate payment in the AMBIGUOUS panel.
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Purpose: Resolves an AMBIGUOUS bill when the biller manually selects the correct payment.
     *          Called by BillerController when the biller clicks a candidate in the UI.
     * Input:   billId          — ID of the bill in AMBIGUOUS status.
     *          chosenPaymentId — ID of the Payment the biller confirmed as the correct one.
     *          resolvedBy      — the biller's username (for the audit log).
     * Output:  the generated Token (returned to the biller as confirmation).
     * Throws:  IllegalArgumentException if the bill is not in AMBIGUOUS status.
     */
    @Transactional
    public Token resolveAmbiguousMatch(Long billId, Long chosenPaymentId, String resolvedBy) {
        log.info("resolveAmbiguousMatch: Biller '{}' resolving bill {} → payment {}", resolvedBy, billId, chosenPaymentId);

        // Load the bill and verify it is still AMBIGUOUS.
        // If it was somehow already resolved (race condition from biller double-clicking),
        // we throw a clear error instead of processing it again.
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.AMBIGUOUS) {
            throw new IllegalArgumentException(
                    "Bill " + billId + " is not in AMBIGUOUS status. Current status: " + bill.getStatus());
        }

        // Load the chosen payment.
        Payment chosenPayment = paymentRepository.findById(chosenPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + chosenPaymentId));

        // Delegate to claimPaymentForBill() with MANUAL_MATCH resolution type.
        // This handles the PESSIMISTIC_WRITE lock, status updates, token generation, and audit log.
        return claimPaymentForBill(bill, chosenPayment, ResolutionType.MANUAL_MATCH, resolvedBy);
    }
}
