package com.osporo.engine.user;

import com.osporo.engine.user.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, UserDirectoryRepository {

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    Boolean existsByEmailAndTenantId(String email, UUID tenantId);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<User> findForTokenIssuanceByEmailAndTenantId(String email, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<User> findForTokenIssuanceByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u FROM User u
            WHERE u.tenantId = :tenantId AND u.id IN (:userIds)
            ORDER BY u.id
            """)
    List<User> lockUsersInTenant(@Param("tenantId") UUID tenantId, @Param("userIds") List<UUID> userIds);

    @Query(value = """
            SELECT u.* FROM users u
            WHERE u.tenant_id = :tenantId
              AND (u.id = :userId OR (
                  u.deleted_at IS NULL AND u.suspended_at IS NULL
                  AND 'MARKETPLACE_OWNER' = ANY(u.roles)
                  AND NOT ('OSPORO_ADMIN' = ANY(u.roles))
              ))
            ORDER BY u.id
            FOR UPDATE
            """, nativeQuery = true)
    List<User> lockAccountAndActiveOwners(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
