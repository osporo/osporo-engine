package com.osporo.engine.shared.exception;

import java.util.UUID;
import com.osporo.engine.shared.enums.ErrorCode;
import org.springframework.http.HttpStatus;

public class TenantSuspendedException extends OsporoException {
    public TenantSuspendedException(UUID tenantId) {
        super(ErrorCode.TENANT_SUSPENDED, HttpStatus.FORBIDDEN,
            "Tenant " + tenantId + " is suspended.");
    }
}
