package com.serveflow.repository;

import com.serveflow.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * TokenRepository — data access layer for generated food pickup tokens.
 *
 * Tokens are generated for both flows:
 *   - Flow A (Campus Bite): one token per confirmed online order.
 *   - Flow B (QuickBill):   one token per confirmed counter bill (cash or matched UPI).
 */
@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    // Fetches the most recently generated tokens, newest first.
    // Used by getLiveBillingStatus() to populate the "Recent Tokens" zone.
    // The service layer applies .subList(0, Math.min(3, list.size())) to get top 3.
    List<Token> findAllByOrderByGeneratedAtDesc();
}
