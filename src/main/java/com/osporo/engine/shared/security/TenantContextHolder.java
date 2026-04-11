package com.osporo.engine.shared.security;

import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.exception.OsporoException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TenantContextHolder {

    private static final ThreadLocal<UUID> tenantContext = new ThreadLocal<>();

    public void setTenantId(UUID tenantId) {
        tenantContext.set(tenantId);
    }

    public UUID getTenantId() {
        UUID tenantId = tenantContext.get();

        if (tenantId == null) {
            throw new OsporoException(
                    ErrorCode.TENANT_CONTEXT_MISSING,
                    HttpStatus.BAD_REQUEST,
                    "No tenant context available for this request."
            );
        }
        return tenantId;
    }

    public void clear() {
        tenantContext.remove();
    }

}
