package com.osporo.engine.tenant;

import com.osporo.engine.tenant.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(
            TenantRepository tenantRepository
    ) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant getTenant(UUID id) {
        return tenantRepository.getReferenceById(id);
    }

    public Page<Tenant> getTenants() {

        return tenantRepository.findAll(Pageable.ofSize(5));
    }

    public void deleteTenant(Tenant tenant) {
        tenantRepository.delete(tenant);
    }

    public Tenant createTenant(Tenant tenant) {
        return tenantRepository.save(tenant);
    }
}
