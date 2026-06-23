package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AdminUser — represents the canteen biller/staff account.
 *
 * There is exactly ONE biller in this single-canteen deployment.
 * The account is seeded at startup by DataInitializer — there is no
 * public registration endpoint for this role.
 *
 * Authentication: username + password + JWT (see AuthController and JwtUtil).
 * Role stored in DB: "ROLE_BILLER" (Spring Security convention requires the "ROLE_" prefix).
 *
 * This class is intentionally kept as "AdminUser" (not renamed to "Biller") to
 * minimise rename churn. In all comments, docs, and UI it is referred to as the Biller.
 *
 * Relationships:
 *   - None directly, but the biller's username is stored as resolvedBy in MatchAttemptLog
 *     whenever they manually resolve an ambiguous payment match.
 */
@Entity
@Table(name = "admin_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The biller's login username. Must be unique across all biller accounts.
    @Column(unique = true, nullable = false)
    private String username;

    // BCrypt-hashed password. Never stored or returned in plain text.
    @Column(nullable = false)
    private String password;

    // Role stored in "ROLE_XXX" format as required by Spring Security.
    // Value: "ROLE_BILLER" for the canteen staff account.
    @Column(nullable = false)
    private String role;

    // Optional contact number for the biller — useful for future SMS/OTP features.
    // Nullable — not required for the current phase.
    private String phone;
}
