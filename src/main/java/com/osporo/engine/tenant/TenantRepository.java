package com.osporo.engine.tenant;

import com.osporo.engine.tenant.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

}
