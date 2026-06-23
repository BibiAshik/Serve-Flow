package com.serveflow.config;

import com.serveflow.security.JwtFilter;
import com.serveflow.security.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * SecurityConfig — configures two authentication mechanisms side by side:
 *
 * CHAIN 1 — JWT (for Biller + Student API calls):
 *   The JwtFilter runs before Spring Security's default username/password filter.
 *   If a valid JWT is found in the Authorization header, the request is authenticated.
 *   This handles: /api/biller/**, /api/online/**, /api/token/**
 *
 * CHAIN 2 — Google OAuth2 (for Campus Bite student portal login):
 *   Spring Boot's OAuth2 client auto-configuration handles the redirect flow.
 *   OAuth2SuccessHandler runs after Google confirms the student's identity.
 *   This handles: /oauth2/authorization/google and the callback /login/oauth2/code/google
 *
 * URL ACCESS RULES (in priority order — more specific rules first):
 *   /api/webhook/razorpay → permitAll()
 *       WHY: Razorpay's servers cannot send a JWT. Security is via HMAC signature.
 *
 *   /api/dev/**           → permitAll()
 *       WHY: Dev simulation endpoint — no JWT needed. Only active when dev-mode=true.
 *            The @ConditionalOnProperty on DevController ensures this is unreachable in prod.
 *
 *   /api/biller/**        → ROLE_BILLER only
 *   /api/food/dropdown    → ROLE_BILLER only (biller's item selector)
 *   /api/online/**        → ROLE_STUDENT only
 *   /api/token/**         → ROLE_BILLER or ROLE_STUDENT (biller reprints, student views own)
 *   /biller/**            → ROLE_BILLER (serving biller HTML pages)
 *   /student/**           → serve to all (JWT validation done in the API calls, not on page load)
 *   /**                   → permitAll() (public pages, static assets, login pages, OAuth2 URIs)
 *
 * SESSION POLICY:
 *   STATELESS for JWT-protected API routes — no server-side session needed.
 *   For OAuth2 flow, Spring needs a brief session to complete the redirect dance —
 *   Spring Boot handles this automatically.
 *
 * CSRF:
 *   Disabled for all API routes because API clients (JS fetch, Razorpay webhooks)
 *   cannot easily include CSRF tokens. The webhook route has a specific comment below.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // enables @PreAuthorize on controller methods
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    public SecurityConfig(JwtFilter jwtFilter, OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    /**
     * Purpose: Provides the BCrypt password encoder used to hash and verify biller passwords.
     * BCrypt is the industry standard for password hashing — it is slow by design,
     * which makes brute-force attacks expensive.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Purpose: Exposes the AuthenticationManager as a Spring bean.
     * The AuthenticationManager is used in AuthController to verify the biller's
     * username + password during login. We use the built-in AuthenticationManager
     * from AuthenticationConfiguration — we do NOT write a custom AuthenticationProvider.
     * Input:   config — Spring's authentication configuration (auto-configured).
     * Output:  The default AuthenticationManager, which uses CustomUserDetailsService.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Purpose: The main security filter chain — defines all URL access rules,
     *          session policy, CSRF settings, and the OAuth2 login flow.
     * Input:   http — the HttpSecurity builder provided by Spring.
     * Output:  A configured SecurityFilterChain bean.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF CONFIGURATION ───────────────────────────────────────────────────
            // We disable CSRF entirely for the API.
            // REASON: Our API clients (JavaScript fetch + Razorpay webhooks) cannot
            // include CSRF tokens. The webhook route specifically MUST be CSRF-exempt
            // because Razorpay's servers are third-party and cannot know our CSRF token.
            // Security for the webhook is enforced instead by HMAC-SHA256 signature
            // verification inside WebhookService.verifyRazorpaySignature().
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/api/**"), // all API routes — stateless JWT
                    new AntPathRequestMatcher("/h2-console/**") // H2 console for dev
                )
            )

            // ── SESSION POLICY ───────────────────────────────────────────────────────
            // STATELESS: Do not create or use HTTP sessions for JWT-authenticated requests.
            // Each API request is self-contained — the JWT carries the identity.
            // Note: OAuth2 login flow temporarily uses a session during the redirect dance,
            // but that is managed automatically by Spring's OAuth2 client — we don't touch it.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── URL ACCESS RULES ─────────────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Razorpay webhook — must be public. Razorpay cannot send a JWT.
                // Security is enforced inside WebhookService (HMAC-SHA256 verification).
                .requestMatchers("/api/webhook/razorpay").permitAll()

                // Dev simulation endpoint — only active when app.dev-mode=true.
                // The @ConditionalOnProperty on DevController ensures the bean doesn't even
                // load when dev-mode is false, making it impossible to call in production.
                .requestMatchers("/api/dev/**").permitAll()

                // H2 console — only accessible in local development (H2 not used in prod).
                .requestMatchers("/h2-console/**").permitAll()

                // OAuth2 login redirect URI and Google callback — must be public.
                // These are handled entirely by Spring Security's OAuth2 client.
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                // Biller API — requires JWT with ROLE_BILLER
                .requestMatchers("/api/biller/**").hasRole("BILLER")

                // Food dropdown — used by the biller's item selector in QuickBill
                .requestMatchers("/api/food/dropdown").hasRole("BILLER")

                // Online order API — requires JWT with ROLE_STUDENT
                .requestMatchers("/api/online/**").hasRole("STUDENT")

                // Token API — accessible by both biller (reprint) and student (own tokens)
                .requestMatchers("/api/token/**").authenticated()

                // Biller HTML pages — served to authenticated billers
                // Note: actual page content is secured, but users can still navigate to
                // the URL. The real security is on the API calls made by the page.
                .requestMatchers("/biller/login").permitAll()  // login page is always public
                .requestMatchers("/biller/**").permitAll()

                // Student login page is always public — anyone can see it
                .requestMatchers("/student/login").permitAll()

                // All other requests — permitAll for static assets, public pages, etc.
                // API security is already handled by the rules above.
                .anyRequest().permitAll()
            )

            // ── GOOGLE OAUTH2 LOGIN ──────────────────────────────────────────────────
            // Spring Boot's OAuth2 client handles the entire redirect flow automatically.
            // We only need to set our custom success handler (domain check + JWT issuance).
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/student/login") // redirect here if OAuth2 session expires
                .successHandler(oAuth2SuccessHandler) // our custom handler runs after Google confirms login
            )

            // ── JWT FILTER ───────────────────────────────────────────────────────────
            // Add our JwtFilter BEFORE the default UsernamePasswordAuthenticationFilter.
            // This ensures JWT authentication is checked first on every request.
            // If a valid JWT is found, Spring Security uses it for the request.
            // If not found, the request continues unauthenticated to the URL rules above.
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

            // ── H2 CONSOLE FRAME SUPPORT ─────────────────────────────────────────────
            // Allow H2 console to display in an iframe (it uses framesets).
            // Only relevant when H2 is active for local dev/testing.
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            );

        return http.build();
    }
}
