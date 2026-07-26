package com.serveflow.repository;

import com.serveflow.entity.RevokedJwtToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RevokedJwtTokenRepository extends JpaRepository<RevokedJwtToken, Long> {

    Optional<RevokedJwtToken> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
