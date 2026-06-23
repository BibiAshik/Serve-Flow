package com.serveflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.List;

/**
 * JwtFilter — intercepts every incoming HTTP request and checks for a valid JWT.
 *
 * This filter MUST extend OncePerRequestFilter — Spring guarantees it runs exactly
 * once per request, not multiple times, which is important for correctness.
 *
 * FILTER LOGIC (in order):
 *   1. Read the "Authorization" header from the incoming request.
 *   2. If the header is missing or does not start with "Bearer ", skip JWT checks.
 *   3. Extract the token from the header.
 *   4. Parse and validate the JWT — extract subject (username/email) and role claim.
 *   5. Build a Spring Security authentication token with the extracted details.
 *   6. Set it on the SecurityContextHolder — request is now authenticated.
 *   7. Continue the filter chain.
 *
 * If the token is invalid or expired, we log a warning and skip step 5-6,
 * leaving the request unauthenticated. Spring Security returns 401 based on
 * the URL rules in SecurityConfig.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    // We inject the secret directly here (same value as JwtUtil)
    // so that the filter can parse tokens independently without calling JwtUtil methods
    // that were designed for generation rather than parsing.
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Purpose: Returns the signing key from the secret string.
     *          Same logic as in JwtUtil — must use the same key to verify signatures.
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Read the Authorization header.
        // Expected format: "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        String authHeader = request.getHeader("Authorization");

        // Step 2: If missing or wrong format, skip JWT processing entirely.
        // This is normal for public endpoints (login page, static files, webhook, etc.)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: Remove "Bearer " prefix (exactly 7 characters) to get the raw token.
        String jwtToken = authHeader.substring(7);

        try {
            // Step 4: Parse and validate the JWT.
            // parseClaimsJws() BOTH validates the signature and checks expiry.
            // If the token is tampered, expired, or malformed, it throws an exception here.
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // verify using the same key used to sign
                    .build()
                    .parseClaimsJws(jwtToken)      // parse and verify in one step
                    .getBody();                    // get the claims payload (subject, role, etc.)

            // Extract the subject — this is the username (for billers) or email (for students).
            String subject = claims.getSubject();

            // Extract the "role" custom claim — set during token generation in JwtUtil.
            // Expected value: "ROLE_BILLER" or "ROLE_STUDENT"
            String role = claims.get("role", String.class);

            // Only proceed if we have a subject, a role, and the SecurityContext is empty.
            // The SecurityContext check prevents overwriting a legitimate existing authentication.
            if (subject != null && role != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Step 5: Build the Spring Security authentication token.
                // UsernamePasswordAuthenticationToken(principal, credentials, authorities)
                //   principal   = subject (username or email) — identifies who is making the request
                //   credentials = null — no password needed; JWT signature already verified identity
                //   authorities = list with the single role extracted from the token
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        subject,
                        null, // credentials not needed after JWT verification
                        List.of(new SimpleGrantedAuthority(role)) // e.g. [ROLE_BILLER]
                );

                // Add request metadata (IP address, session ID) for audit purposes.
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Step 6: Register this authentication with Spring Security.
                // After this line, the request is considered authenticated.
                // Spring Security's @PreAuthorize and URL matchers will check the role.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (Exception e) {
            // If the token is invalid, expired, or tampered:
            //   - Log a warning (don't log the full token — it may contain user info).
            //   - Do NOT set authentication — request stays unauthenticated.
            //   - Do NOT re-throw — just let the filter chain continue.
            // Spring Security's URL rules will then return 401 for protected endpoints.
            log.warn("JWT validation failed for '{}': {}", request.getRequestURI(), e.getMessage());
        }

        // Step 7: Always continue the filter chain regardless of authentication outcome.
        filterChain.doFilter(request, response);
    }
}
