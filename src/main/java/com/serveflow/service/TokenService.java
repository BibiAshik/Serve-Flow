package com.serveflow.service;

import com.serveflow.dto.response.TokenResponseDTO;
import com.serveflow.entity.*;
import com.serveflow.mapper.TokenMapper;
import com.serveflow.repository.TokenRepository;
import com.serveflow.repository.TokenSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TokenService — generates and reprints food pickup tokens.
 *
 * TOKEN NUMBER GENERATION — CONCURRENCY SAFE:
 *   We use the TokenSequence entity (a single-row table with a counter) and
 *   a PESSIMISTIC_WRITE lock to generate sequential token numbers safely.
 *   This guarantees uniqueness even when two requests arrive simultaneously.
 *
 *   WHY NOT SELECT MAX() + 1?
 *     Two simultaneous calls could both read the same MAX value (e.g. 42),
 *     both compute 43, and both issue token #43 to different customers.
 *     With the PESSIMISTIC_WRITE lock, the second call waits for the first
 *     to commit (which increments the counter to 43), then reads 43 and
 *     issues token #44. No duplicates are possible.
 *
 * After generating a token, this service calls PrinterService.printToken().
 * If the printer is offline, PrinterService updates the status to PRINT_FAILED
 * and prepares the virtualPrintHtml for on-screen display.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final TokenRepository tokenRepository;
    private final TokenSequenceRepository tokenSequenceRepository;
    private final PrinterService printerService;
    private final TokenMapper tokenMapper;

    public TokenService(TokenRepository tokenRepository,
                        TokenSequenceRepository tokenSequenceRepository,
                        PrinterService printerService,
                        TokenMapper tokenMapper) {
        this.tokenRepository = tokenRepository;
        this.tokenSequenceRepository = tokenSequenceRepository;
        this.printerService = printerService;
        this.tokenMapper = tokenMapper;
    }

    /**
     * Purpose: Generates a token for a confirmed COUNTER bill (Flow B — QuickBill).
     *          Used by claimPaymentForBill() after a UPI match, and by BillingService
     *          for CASH payments.
     * Input:   bill — the confirmed Bill entity (MATCHED or CASH_CONFIRMED status).
     * Output:  the saved Token entity with a sequential token number.
     */
    @Transactional
    public Token generateToken(Bill bill) {
        log.info("generateToken(Bill): Generating token for bill {}", bill.getId());

        // Get the next token number safely using the row-level lock.
        long nextTokenNumber = getNextTokenNumber();

        // Build the human-readable item summary (e.g. "Chicken Fried Rice x2").
        String itemSummary = buildItemSummary(bill.getItemName(), bill.getQuantity());

        // Create and populate the token entity.
        Token token = new Token();
        token.setTokenNumber(nextTokenNumber);
        token.setBill(bill);       // link to the counter bill
        token.setOrder(null);      // this is a counter token, not an online order token
        token.setItemSummary(itemSummary);
        token.setAmount(bill.getAmount());
        token.setStatus(TokenStatus.ACTIVE); // starts as ACTIVE; printer changes to PRINTED or PRINT_FAILED
        token.setGeneratedAt(LocalDateTime.now());
        token.setPrintedAt(null); // will be set by PrinterService if printing succeeds

        // Save the token to the database.
        Token savedToken = tokenRepository.save(token);
        log.info("generateToken(Bill): Token #{} created (id={})", nextTokenNumber, savedToken.getId());

        // Attempt to print the token on the physical printer.
        // PrinterService handles the offline fallback internally.
        printerService.printToken(savedToken);

        return savedToken;
    }

    /**
     * Purpose: Generates a token for a confirmed ONLINE order (Flow A — Campus Bite).
     *          Used by OnlineOrderService after Razorpay payment is verified.
     * Input:   order — the Order entity with paymentStatus = PAID.
     * Output:  the saved Token entity.
     */
    @Transactional
    public Token generateToken(Order order) {
        log.info("generateToken(Order): Generating token for online order {}", order.getId());

        long nextTokenNumber = getNextTokenNumber();

        // Build item summary from the order's items list.
        StringBuilder summaryBuilder = new StringBuilder();
        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItem item = order.getItems().get(i);
            summaryBuilder.append(item.getFoodItem().getName())
                          .append(" x")
                          .append(item.getQuantity());
            if (i < order.getItems().size() - 1) {
                summaryBuilder.append(", ");
            }
        }

        Token token = new Token();
        token.setTokenNumber(nextTokenNumber);
        token.setBill(null);       // this is an online order token, not a counter bill token
        token.setOrder(order);
        token.setItemSummary(summaryBuilder.toString());
        token.setAmount(BigDecimal.valueOf(order.getTotalAmount()));
        token.setStatus(TokenStatus.ACTIVE);
        token.setGeneratedAt(LocalDateTime.now());
        token.setPrintedAt(null);

        Token savedToken = tokenRepository.save(token);
        log.info("generateToken(Order): Token #{} created (id={})", nextTokenNumber, savedToken.getId());

        // For online orders, we do not auto-print at the counter.
        // The student views their token on the my-orders page.
        // We still call printToken to log the attempt, but PrinterService may skip
        // printing for ONLINE type tokens in a future enhancement.
        printerService.printToken(savedToken);

        return savedToken;
    }

    /**
     * Purpose: Re-sends an existing token to the printer (called by TokenController /reprint).
     *          Used by the biller when clicking "Reprint" in the Recent Tokens zone.
     * Input:   tokenId — the ID of the token to reprint.
     * Output:  the updated Token entity with a new printedAt timestamp.
     */
    @Transactional
    public Token reprintToken(Long tokenId) {
        log.info("reprintToken: Reprinting token {}", tokenId);

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));

        // Reset to ACTIVE so PrinterService treats it as a fresh print attempt.
        token.setStatus(TokenStatus.ACTIVE);
        token.setPrintedAt(null);
        Token savedToken = tokenRepository.save(token);

        printerService.printToken(savedToken);

        return savedToken;
    }

    /**
     * Purpose: Gets the next sequential token number using a PESSIMISTIC_WRITE lock.
     *          This is the heart of the concurrency-safe numbering approach.
     * Input:   none.
     * Output:  the next token number (lastUsedNumber + 1).
     *
     * HOW IT WORKS:
     *   1. Lock the single row in token_sequence with PESSIMISTIC_WRITE.
     *      Other calls to getNextTokenNumber() will block here until we commit.
     *   2. Read the current lastUsedNumber.
     *   3. Increment it by 1.
     *   4. Save the incremented value back to the row.
     *   5. Return the new number as the token number to use.
     *   When our transaction commits, the lock is released and any waiting call reads
     *   the updated value (e.g. 43) and increments to 44. No duplicate ever possible.
     */
    @Transactional
    public long getNextTokenNumber() {
        // Acquire exclusive lock on the single TokenSequence row.
        TokenSequence sequence = tokenSequenceRepository.findSequenceWithLock()
                .orElseThrow(() -> new IllegalStateException(
                        "TokenSequence row not found. DataInitializer may not have run."));

        // Increment the counter.
        long nextNumber = sequence.getLastUsedNumber() + 1;

        // Save the updated counter back to the database.
        sequence.setLastUsedNumber(nextNumber);
        tokenSequenceRepository.save(sequence);

        return nextNumber;
    }

    /**
     * Purpose: Builds a human-readable item summary string for display on the token slip.
     * Input:   itemName — the name of the item (e.g. "Chicken Fried Rice").
     *          quantity — the number of units (e.g. 2).
     * Output:  e.g. "Chicken Fried Rice x2"
     */
    private String buildItemSummary(String itemName, Integer quantity) {
        if (itemName == null) itemName = "Item";
        if (quantity == null) quantity = 1;
        return itemName + " x" + quantity;
    }
}
