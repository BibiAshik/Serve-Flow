package com.serveflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtUtil — utility class for creating and validating JSON Web Tokens (JWTs).
 *
 * JWTs are used for authentication in both portals:
 *   - Biller: receives a JWT after login via POST /api/auth/biller/login.
 *             The JWT contains the biller's username and role (ROLE_BILLER).
 *   - Student: receives a JWT after Google OAuth2 login is verified by OAuth2SuccessHandler.
 *              The JWT contains the student's email and role (ROLE_STUDENT).
 *
 * The JWT secret and expiry time are read from application.properties:
 *   jwt.secret         — must be a long random string (at least 32 characters).
 *   jwt.expiration-ms  — token lifetime in milliseconds (default: 86400000 = 24 hours).
 *
 * NEVER hardcode the secret in Java source code. It belongs in application.properties.
 *
 * How the JWT flow works:
 *   1. Client logs in → backend generates a JWT → client stores it in localStorage.
 *   2. Client attaches JWT as "Authorization: Bearer <token>" on every API request.
 *   3. JwtFilter reads the header, validates the token, and sets the SecurityContext.
 *   4. Spring Security then allows or denies the request based on the role in the token.
 */
@Component
public class JwtUtil {

    // The secret key used to sign JWTs. Injected from application.properties.
    // Using @Value means it is never hardcoded — changing the property changes all future tokens.
    @Value("${jwt.secret}")
    private String secret;

    // How long a token is valid after creation, in milliseconds.
    // Injected from application.properties. Default: 86400000 ms = 24 hours.
    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Purpose: Converts the raw secret string from application.properties into a
     *          cryptographically usable signing key.
     * Input:   Nothing — reads from the injected 'secret' field.
     * Output:  A Key object that JJWT uses to sign and verify tokens.
     */
    private Key getSigningKey() {
        // Keys.hmacShaKeyFor() creates an HMAC-SHA key from the secret bytes.
        // This is the recommended JJWT way to create a signing key.
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Purpose: Generates a JWT token for an authenticated user.
     * Input:   userDetails — the authenticated user (biller or student).
     *          The username field in userDetails is used as the JWT subject.
     *          The first authority in userDetails is stored as the "role" claim.
     * Output:  A signed JWT string that the client stores and sends with each request.
     */
    public String generateToken(UserDetails userDetails) {
        // Build a map of extra claims to embed in the token.
        // We store the user's role (e.g. ROLE_BILLER or ROLE_STUDENT) as a custom claim
        // so the frontend can use it to decide which portal to render.
        Map<String, Object> extraClaims = new HashMap<>();
        if (!userDetails.getAuthorities().isEmpty()) {
            // getAuthority() returns the role string, e.g. "ROLE_BILLER"
            extraClaims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        }

        return buildToken(extraClaims, userDetails.getUsername());
    }

    /**
     * Purpose: Generates a JWT for a student authenticated via Google OAuth2.
     *          Unlike buildToken(UserDetails), this version takes the email and role directly
     *          because the student does not have a UserDetails object in the traditional sense.
     * Input:   email — the student's Google email (e.g. "john@sairamtap.edu.in").
     *          role  — always "ROLE_STUDENT" for students.
     * Output:  A signed JWT string.
     */
    public String generateTokenForStudent(String email, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        return buildToken(extraClaims, email);
    }

    /**
     * Purpose: Constructs the actual JWT string with claims, subject, issue time, and expiry.
     * Input:   extraClaims — map of custom claims to include (e.g. role).
     *          subject     — the "sub" claim (username or email).
     * Output:  A signed, compact JWT string.
     */
    private String buildToken(Map<String, Object> extraClaims, String subject) {
        long nowMs = System.currentTimeMillis();

        return Jwts.builder()
                .setClaims(extraClaims)            // add role and any other extra claims
                .setSubject(subject)               // sub = username or email
                .setIssuedAt(new Date(nowMs))      // iat = current time
                .setExpiration(new Date(nowMs + expirationMs)) // exp = current + 24h
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // sign with HMAC-SHA256
                .compact();                        // build and return the token string
    }

    /**
     * Purpose: Extracts the subject (username or email) from a JWT.
     * Input:   token — the raw JWT string from the Authorization header.
     * Output:  The subject string embedded in the token (username or student email).
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Purpose: Checks whether a JWT is valid for the given user.
     * Input:   token       — the JWT string to validate.
     *          userDetails — the user loaded from the database to verify against.
     * Output:  true if the token username matches and the token has not expired.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String usernameInToken = extractUsername(token);
        // Token is valid only if the username matches AND the token is not yet expired.
        return usernameInToken.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Purpose: Validates a token for a student using their email (not a UserDetails object).
     * Input:   token — the JWT string.
     *          email — the student's email to match against the token subject.
     * Output:  true if the email matches and the token is not expired.
     */
    public boolean isTokenValidForEmail(String token, String email) {
        String subjectInToken = extractUsername(token);
        return subjectInToken.equals(email) && !isTokenExpired(token);
    }

    /**
     * Purpose: Checks if a token has passed its expiration time.
     * Input:   token — the JWT string.
     * Output:  true if the token's expiration date is in the past.
     */
    private boolean isTokenExpired(String token) {
        Date expirationDate = extractAllClaims(token).getExpiration();
        return expirationDate.before(new Date()); // true if expiry is before now
    }

    /**
     * Purpose: Parses the JWT and returns all embedded claims.
     * Input:   token — the JWT string.
     * Output:  Claims object containing all key-value pairs embedded in the token.
     * Throws:  JwtException (from JJWT) if the token is invalid, tampered, or expired.
     *          JwtFilter catches this and treats it as an unauthenticated request.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey()) // use the same key that was used to sign
                .build()
                .parseClaimsJws(token)          // parse and verify the token
                .getBody();                     // get the claims payload
    }
}
