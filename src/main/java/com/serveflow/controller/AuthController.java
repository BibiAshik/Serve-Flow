package com.serveflow.controller;

import com.serveflow.dto.request.BillerLoginRequestDTO;
import com.serveflow.security.JwtUtil;
import com.serveflow.service.JwtRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

/**
 * AuthController — handles biller authentication (username + password → JWT).
 *
 * BILLER LOGIN FLOW:
 *   1. Biller enters username + password on QuickBill login.html.
 *   2. POST /api/auth/biller/login is called with BillerLoginRequestDTO.
 *   3. AuthenticationManager verifies credentials against the DB (via CustomUserDetailsService).
 *   4. If valid, JwtUtil generates a JWT and it is returned to the frontend.
 *   5. billing.js stores the JWT in localStorage and attaches it to all future API calls.
 *
 * STUDENT AUTHENTICATION:
 *   Students use Google OAuth2 — they do NOT go through this controller.
 *   Their JWTs are issued by OAuth2SuccessHandler after the Google redirect flow.
 *
 * NOTE: We use the built-in AuthenticationManager — we do NOT write a custom
 * AuthenticationProvider. The manager automatically uses CustomUserDetailsService.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final JwtRevocationService jwtRevocationService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          JwtRevocationService jwtRevocationService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.jwtRevocationService = jwtRevocationService;
    }

    /**
     * Purpose: Authenticates the biller and returns a JWT token.
     * Input:   BillerLoginRequestDTO — username and password from the login form.
     * Output:  { "token": "eyJ...", "username": "admin", "role": "ROLE_BILLER" }
     *          HTTP 401 if credentials are invalid (Spring Security handles this automatically).
     */
    @PostMapping("/biller/login")
    public ResponseEntity<Map<String, String>> billerLogin(@Valid @RequestBody BillerLoginRequestDTO loginRequest) {

        // Attempt authentication using the built-in AuthenticationManager.
        // It calls CustomUserDetailsService.loadUserByUsername() internally.
        // If credentials are wrong, AuthenticationException is thrown and Spring returns 401.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        // Authentication succeeded — extract the UserDetails from the result.
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Generate a JWT for the authenticated biller.
        // The JWT contains the username (as subject) and role (as a custom claim).
        String jwtToken = jwtUtil.generateToken(userDetails);

        // Return the token along with the username and role.
        // The frontend stores the token in localStorage for subsequent API calls.
        return ResponseEntity.ok(Map.of(
                "token", jwtToken,
                "username", userDetails.getUsername(),
                "role", userDetails.getAuthorities().iterator().next().getAuthority()
        ));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token != null) {
            Date expiresAt = jwtUtil.extractExpiration(token);
            jwtRevocationService.revoke(token, expiresAt);
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
