package com.serveflow.repository;

import com.serveflow.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AdminUserRepository — data access layer for the AdminUser (Biller) entity.
 *
 * Used by:
 *   - CustomUserDetailsService: to load biller credentials for JWT authentication.
 *   - DataInitializer: to check if the biller account exists before seeding.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    // Finds a biller by their login username.
    // Returns Optional.empty() if no user with that username exists.
    // Used by CustomUserDetailsService for Spring Security authentication.
    Optional<AdminUser> findByUsername(String username);
}
