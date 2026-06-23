package com.serveflow.controller;

import com.serveflow.dto.request.BillCreateRequestDTO;
import com.serveflow.dto.request.ResolveMatchRequestDTO;
import com.serveflow.dto.response.BillResponseDTO;
import com.serveflow.dto.response.LiveStatusDTO;
import com.serveflow.dto.response.TokenResponseDTO;
import com.serveflow.entity.FoodItem;
import com.serveflow.entity.ResolutionType;
import com.serveflow.entity.Token;
import com.serveflow.mapper.TokenMapper;
import com.serveflow.service.BillingService;
import com.serveflow.service.FoodService;
import com.serveflow.service.PaymentMatchingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * BillerController — handles core QuickBill billing operations.
 *
 * All endpoints require ROLE_BILLER (enforced at both URL level in SecurityConfig
 * and method level via @PreAuthorize — both layers for defense in depth).
 *
 * Endpoints:
 *   POST /api/biller/bills         — create a new counter bill (cash or UPI)
 *   GET  /api/biller/live-status   — polling endpoint for QuickBill main screen (every 2.5s)
 *   POST /api/biller/resolve-match — biller manually resolves an AMBIGUOUS bill
 */
@RestController
@RequestMapping("/api/biller")
@PreAuthorize("hasRole('BILLER')") // all methods in this controller require ROLE_BILLER
public class BillerController {

    private final BillingService billingService;
    private final FoodService foodService;
    private final PaymentMatchingService paymentMatchingService;
    private final TokenMapper tokenMapper;

    public BillerController(BillingService billingService,
                            FoodService foodService,
                            PaymentMatchingService paymentMatchingService,
                            TokenMapper tokenMapper) {
        this.billingService = billingService;
        this.foodService = foodService;
        this.paymentMatchingService = paymentMatchingService;
        this.tokenMapper = tokenMapper;
    }

    /**
     * Purpose: Creates a new counter bill from the QuickBill "Create Bill" form.
     *          If CASH: token generated immediately and returned in the response.
     *          If UPI: bill saved as WAITING_PAYMENT; matching engine invoked.
     * Input:   BillCreateRequestDTO — item details and payment mode from the form.
     * Output:  BillResponseDTO with created bill status and token (if generated).
     */
    @PostMapping("/bills")
    public ResponseEntity<BillResponseDTO> createBill(@Valid @RequestBody BillCreateRequestDTO request) {

        // Optionally look up the FoodItem if foodItemId was provided.
        // If null, the biller typed a custom item name (foodItem will be null in the entity).
        FoodItem foodItem = null;
        if (request.getFoodItemId() != null) {
            foodItem = foodService.getFoodItemById(request.getFoodItemId());
            // If a food item ID was provided but not found, we still proceed with the typed name.
        }

        BillResponseDTO billResponse = billingService.createBill(
                request.getItemName(),
                request.getQuantity(),
                request.getUnitRate(),
                request.getPaymentMode(),
                foodItem
        );

        return ResponseEntity.ok(billResponse);
    }

    /**
     * Purpose: Returns the complete live billing status for QuickBill screen rendering.
     *          Called by billing.js every 2500ms via setInterval.
     *          Re-renders: pending bills, ambiguous matches, recent tokens, payments, counts.
     * Input:   none (no request body — this is a GET).
     * Output:  LiveStatusDTO — the complete snapshot of the current billing state.
     */
    @GetMapping("/live-status")
    public ResponseEntity<LiveStatusDTO> getLiveStatus() {
        LiveStatusDTO liveStatus = billingService.getLiveBillingStatus();
        return ResponseEntity.ok(liveStatus);
    }

    /**
     * Purpose: Resolves an AMBIGUOUS bill — biller manually selects the correct payment.
     *          Called when the biller clicks a candidate payment in the MULTIPLE MATCH panel.
     * Input:   ResolveMatchRequestDTO — billId and the chosen payment ID.
     *          Authentication — extracted from JWT to get the biller's username for the audit log.
     * Output:  TokenResponseDTO — the generated token after resolution.
     */
    @PostMapping("/resolve-match")
    public ResponseEntity<TokenResponseDTO> resolveMatch(@Valid @RequestBody ResolveMatchRequestDTO request,
                                                          Authentication authentication) {

        // Extract the biller's username from the JWT-based authentication.
        // This is stored in the MatchAttemptLog as resolvedBy for the audit trail.
        String billerUsername = authentication.getName();

        Token generatedToken = paymentMatchingService.resolveAmbiguousMatch(
                request.getBillId(),
                request.getChosenPaymentId(),
                billerUsername
        );

        return ResponseEntity.ok(tokenMapper.toDTO(generatedToken));
    }
}
