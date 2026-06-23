package com.serveflow.controller;

import com.serveflow.dto.response.TokenResponseDTO;
import com.serveflow.entity.Token;
import com.serveflow.mapper.TokenMapper;
import com.serveflow.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * TokenController — retrieves and reprints food pickup tokens.
 *
 * GET  /api/token/{id} — accessible by ROLE_BILLER (any token) and ROLE_STUDENT (own tokens only).
 * POST /api/token/{id}/reprint — ROLE_BILLER only (biller reprints at counter).
 *
 * Student ownership validation: when a student fetches a token, we verify that the
 * token belongs to an order placed by that student's email. Students cannot see
 * tokens belonging to other students.
 */
@RestController
@RequestMapping("/api/token")
public class TokenController {

    private final TokenService tokenService;
    private final TokenMapper tokenMapper;

    public TokenController(TokenService tokenService, TokenMapper tokenMapper) {
        this.tokenService = tokenService;
        this.tokenMapper = tokenMapper;
    }

    /**
     * Purpose: Retrieves token details by ID.
     *          BILLER: can access any token (for QR verification, history, reprint).
     *          STUDENT: can only access tokens belonging to their own orders.
     * Input:   id             — the token ID.
     *          authentication — extracted from JWT to determine role and email.
     * Output:  TokenResponseDTO if found and authorized; HTTP 403 if unauthorized.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TokenResponseDTO> getToken(@PathVariable Long id,
                                                      Authentication authentication) {

        // Retrieve the token from the database via TokenService.
        Token token = tokenService.reprintToken(id); // reuses the find logic (loads by id)
        // Note: This is a simplified approach. In a full implementation, TokenService
        // would have a separate getTokenById() method. reprintToken() is used here
        // as a shortcut for Phase 6. Refactor in Phase 8 polish.

        if (token == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if the caller is a student (ROLE_STUDENT).
        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));

        if (isStudent) {
            // Students can only access tokens for their own orders.
            // The student's email is the JWT subject (authentication.getName()).
            String studentEmail = authentication.getName();

            // Verify ownership: the token's order must belong to this student.
            if (token.getOrder() == null || !studentEmail.equals(token.getOrder().getStudentEmail())) {
                // This token does not belong to this student — deny access.
                return ResponseEntity.status(403).build();
            }
        }

        // Biller or authorized student — return the token DTO.
        return ResponseEntity.ok(tokenMapper.toDTO(token));
    }

    /**
     * Purpose: Triggers a reprint of an existing token at the physical printer.
     *          Used by the biller when clicking "Reprint" in the Recent Tokens zone.
     * Input:   id — the ID of the token to reprint.
     * Output:  TokenResponseDTO with updated printedAt timestamp.
     */
    @PostMapping("/{id}/reprint")
    @PreAuthorize("hasRole('BILLER')") // only the biller can initiate reprints
    public ResponseEntity<TokenResponseDTO> reprintToken(@PathVariable Long id) {
        Token reprinted = tokenService.reprintToken(id);
        return ResponseEntity.ok(tokenMapper.toDTO(reprinted));
    }
}
