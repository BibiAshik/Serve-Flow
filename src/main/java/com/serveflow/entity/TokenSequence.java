package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TokenSequence — a single-row table used for concurrency-safe token number generation.
 *
 * WHY THIS EXISTS:
 *   We need sequential token numbers (e.g. 1, 2, 3, ...) that are unique even under
 *   concurrent requests. A naive approach of SELECT MAX(tokenNumber) + 1 has a race
 *   condition: if two requests run simultaneously, both could read the same MAX value
 *   and generate duplicate token numbers.
 *
 *   Instead, TokenService locks this single row with PESSIMISTIC_WRITE before reading
 *   and incrementing the counter. This means only one request can read-and-increment
 *   at a time. The database lock ensures correctness even under heavy concurrency.
 *
 * TABLE STRUCTURE:
 *   - Always contains exactly ONE row (id = 1).
 *   - lastUsedNumber starts at 0 and increments by 1 for each token generated.
 *   - DataInitializer seeds this row at startup if it doesn't already exist.
 *
 * HOW TOKENSERVICE USES IT:
 *   1. Start a @Transactional method.
 *   2. Lock the row: SELECT * FROM token_sequence WHERE id=1 FOR UPDATE (PESSIMISTIC_WRITE).
 *   3. Read lastUsedNumber, add 1, store it as the new token number.
 *   4. Save the row back with the incremented value.
 *   5. Use the new number for the Token being generated.
 */
@Entity
@Table(name = "token_sequence")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenSequence {

    @Id
    private Long id; // Always 1 — there is exactly one row in this table.

    // The last token number that was issued. Starts at 0 (meaning no tokens yet).
    // Each call to TokenService.generateToken() increments this by 1 and uses the result.
    private Long lastUsedNumber;
}
