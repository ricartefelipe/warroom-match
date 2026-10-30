package br.com.ricarte.warroom.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LoginTokenRepository extends JpaRepository<LoginToken, UUID> {

    Optional<LoginToken> findByTokenHash(String tokenHash);

    /** Consumes a live token atomically: exactly one caller gets 1, every other caller gets 0. */
    @Modifying
    @Query("delete from LoginToken t where t.tokenHash = :tokenHash and t.expiresAt > :now")
    int consume(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
