package com.serveflow.security;

import com.serveflow.exception.InvalidDomainException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import com.serveflow.entity.Student;
import com.serveflow.repository.StudentRepository;

/**
 * OAuth2SuccessHandler — called by Spring Security after a student successfully
 * authenticates with Google via OAuth2.
 *
 * WHAT THIS DOES:
 *   1. Extracts the student's email from the Google OAuth2 token.
 *   2. Validates that the email ends with the allowed college domain (@sairamtap.edu.in).
 *   3. If the domain is valid:
 *      - Issues a JWT for the student (with role = ROLE_STUDENT).
 *      - Redirects to the Campus Bite home page: /student/home?token=<jwt>
 *        (The frontend stores the token in localStorage for subsequent API calls.)
 *   4. If the domain is INVALID:
 *      - Redirects to /student/login?error=invalid-domain
 *      - The login page shows the error message to the student.
 *
 * WHY WE ISSUE A JWT AFTER OAUTH2:
 *   Spring Security's OAuth2 session is server-side. Our API endpoints use stateless
 *   JWT authentication. So after Google confirms the student's identity, we issue
 *   our own JWT for the student — from then on, the student uses that JWT just like
 *   the biller does, via the "Authorization: Bearer <token>" header.
 *
 * COLLEGE EMAIL DOMAIN:
 *   Configured via app.college-email-domain in application.properties.
 *   Default: @sairamtap.edu.in
 *   Only students with this domain can access Campus Bite.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final JwtUtil jwtUtil;
    private final StudentRepository studentRepository;

    // The allowed college domain — read from application.properties.
    // Example value: "@sairamtap.edu.in"
    @Value("${app.college-email-domain}")
    private String allowedEmailDomain;

    public OAuth2SuccessHandler(JwtUtil jwtUtil, StudentRepository studentRepository) {
        this.jwtUtil = jwtUtil;
        this.studentRepository = studentRepository;
    }

    /**
     * Purpose: Called by Spring Security automatically after Google OAuth2 login succeeds.
     * Input:   request        — the HTTP request.
     *          response       — the HTTP response (we call sendRedirect on this).
     *          authentication — the OAuth2AuthenticationToken from Google containing user info.
     * Output:  A redirect to Campus Bite home (with JWT) or the login error page.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // Step 1: Cast the authentication to OAuth2AuthenticationToken to access Google's data.
        // This is safe because Spring Security only calls this handler after a successful OAuth2 login.
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        // Step 2: Get the OAuth2User — this contains attributes returned by Google:
        //   "email", "name", "picture", "sub" (Google user ID), etc.
        OAuth2User oauthUser = oauthToken.getPrincipal();

        // Step 3: Extract the student's email from the Google account.
        // Google always provides the email attribute if the "email" scope was requested.
        String email = oauthUser.getAttribute("email");

        // Safety check: if for any reason email is null, reject the login.
        if (email == null) {
            log.warn("OAuth2 login succeeded but email was null. Rejecting.");
            response.sendRedirect("/student/login?error=no-email");
            return;
        }

        // Step 4: Check if the email ends with the allowed college domain.
        // We use endsWith() — "@sairamtap.edu.in" matches "john@sairamtap.edu.in".
        if (!email.endsWith(allowedEmailDomain)) {
            // Domain check FAILED — this is not a college student's account.
            // Log the attempt (without the full email for privacy).
            log.warn("OAuth2 login rejected: email domain not allowed. Email ends with: {}",
                     email.substring(email.indexOf('@')));

            // Redirect to the login page with an error parameter.
            // The login.html page reads this parameter and shows the error message.
            response.sendRedirect("/student/login?error=invalid-domain");
            return;
        }

        // Step 5: Domain is valid. Extract the student's display name from Google.
        String name = oauthUser.getAttribute("name");
        log.info("Student authenticated via Google OAuth2: {} ({})", name, email);

        // Ensure the student exists in our database
        studentRepository.findByEmail(email).orElseGet(() -> {
            Student newStudent = new Student();
            newStudent.setEmail(email);
            newStudent.setName(name);
            return studentRepository.save(newStudent);
        });

        // Step 6: Generate a JWT for this student.
        // We create a minimal UserDetails object just for the JWT generation method.
        // The role is ROLE_STUDENT — stored in the JWT so JwtFilter can read it later.
        UserDetails studentDetails = new User(
                email,  // username = email (used as the JWT subject)
                "",     // no password — Google handled authentication
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
        String jwtToken = jwtUtil.generateToken(studentDetails);

        // Step 7: Redirect the student to the Campus Bite home page.
        // We pass the JWT as a URL parameter so the JavaScript on home.html
        // can store it in localStorage for future API calls.
        // Note: Passing JWT in URL is acceptable for this phase. For higher security,
        // consider using an HttpOnly cookie — documented as a future improvement.
        response.sendRedirect("/student/home?token=" + jwtToken + "&name=" + name);
    }
}
