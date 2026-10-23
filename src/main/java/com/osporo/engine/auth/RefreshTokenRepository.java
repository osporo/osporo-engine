package com.osporo.engine.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.osporo.engine.auth.model.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUserIdAndTenantId(UUID userId, UUID tenantId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken token
            SET token.invalidated = true
            WHERE token.userId = :userId
              AND token.tenantId = :tenantId
              AND token.invalidated = false
            """)
    int invalidateByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
