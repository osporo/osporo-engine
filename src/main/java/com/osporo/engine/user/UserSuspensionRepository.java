package com.osporo.engine.user;

import com.osporo.engine.user.model.UserSuspension;
import com.osporo.engine.user.model.UserSuspension.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSuspensionRepository extends JpaRepository<UserSuspension, UUID> {

    Optional<UserSuspension> findFirstByTenantIdAndUserIdAndActionAndActionDateOrderByIdAsc(
            UUID tenantId, UUID userId, Action action, OffsetDateTime actionDate
    );
}
