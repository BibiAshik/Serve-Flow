package com.serveflow.service;

import com.serveflow.entity.RevokedJwtToken;
import com.serveflow.repository.RevokedJwtTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;

@Service
public class JwtRevocationService {

    private final RevokedJwtTokenRepository revokedJwtTokenRepository;

    public JwtRevocationService(RevokedJwtTokenRepository revokedJwtTokenRepository) {
        this.revokedJwtTokenRepository = revokedJwtTokenRepository;
    }

    @Transactional
    public void revoke(String token, Date expiresAt) {
        cleanupExpiredTokens();

        String tokenHash = hashToken(token);
        if (revokedJwtTokenRepository.findByTokenHash(tokenHash).isPresent()) {
            return;
        }

        LocalDateTime expiry = LocalDateTime.ofInstant(expiresAt.toInstant(), ZoneId.systemDefault());
        revokedJwtTokenRepository.save(new RevokedJwtToken(null, tokenHash, expiry));
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        return revokedJwtTokenRepository.findByTokenHash(hashToken(token)).isPresent();
    }

    @Transactional
    public void cleanupExpiredTokens() {
        revokedJwtTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
