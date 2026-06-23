package com.serveflow.repository;

import com.serveflow.entity.TokenSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * TokenSequenceRepository — data access layer for the single-row token counter.
 *
 * This repository has one critical method: findSequenceWithLock().
 * It acquires a PESSIMISTIC_WRITE (row-level exclusive) lock on the single row
 * in the token_sequence table before TokenService reads and increments the counter.
 *
 * WHY THE LOCK IS ESSENTIAL:
 *   Without the lock, two simultaneous token generation requests could both read
 *   lastUsedNumber = 42, both compute 43 as the next number, and both issue
 *   token #43 to different customers. With the lock, the second request waits
 *   until the first transaction commits, then reads 43 and issues token #44.
 */
@Repository
public interface TokenSequenceRepository extends JpaRepository<TokenSequence, Long> {

    // Acquires an exclusive lock on the single TokenSequence row (id = 1).
    // Called inside a @Transactional method in TokenService.
    // The lock is automatically released when the transaction commits.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ts FROM TokenSequence ts WHERE ts.id = 1")
    Optional<TokenSequence> findSequenceWithLock();
}
