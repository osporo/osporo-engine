package com.osporo.engine.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import com.osporo.engine.shared.enums.ErrorCode;

public class TenantNotFoundException extends OsporoException {
    public TenantNotFoundException(UUID tenantId) {
        super(
            ErrorCode.TENANT_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "Tenant " + tenantId + " was not found"
        );
    }
}
