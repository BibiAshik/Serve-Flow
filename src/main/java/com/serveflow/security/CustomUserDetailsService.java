package com.serveflow.security;

import com.serveflow.entity.AdminUser;
import com.serveflow.repository.AdminUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CustomUserDetailsService — loads biller details from the database for Spring Security.
 *
 * Spring Security calls this service during the JWT login flow to verify that
 * the username in the login request matches a real biller account in the database.
 *
 * This is ONLY used for the BILLER login flow (username + password).
 * Students authenticate via Google OAuth2 — they never touch this service.
 *
 * How it fits in the flow:
 *   1. Biller sends POST /api/auth/biller/login with username + password.
 *   2. AuthController passes credentials to AuthenticationManager.
 *   3. AuthenticationManager calls this service's loadUserByUsername().
 *   4. This service loads the AdminUser from DB and returns a UserDetails object.
 *   5. Spring Security verifies the password against the BCrypt hash.
 *   6. If correct, AuthController generates a JWT and returns it.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    // Constructor injection — allows easier unit testing without @Autowired.
    public CustomUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    /**
     * Purpose: Load a biller's account from the database by their username.
     * Input:   username — the username the biller typed in the login form.
     * Output:  A UserDetails object that Spring Security uses to verify the password.
     * Throws:  UsernameNotFoundException if no biller with that username exists.
     *          Spring Security catches this and returns a 401 response.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Look up the AdminUser (biller) by username in the database.
        // orElseThrow() gives a clean error message if the username does not exist.
        AdminUser adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Biller account not found for username: " + username));

        // Build and return a Spring Security UserDetails object.
        // We use SimpleGrantedAuthority instead of roles() to avoid Spring adding "ROLE_"
        // prefix automatically — our DB already stores "ROLE_BILLER" with the prefix.
        return new User(
                adminUser.getUsername(),
                adminUser.getPassword(), // BCrypt hash — Spring Security will verify this
                List.of(new SimpleGrantedAuthority(adminUser.getRole())) // e.g. ROLE_BILLER
        );
    }
}
