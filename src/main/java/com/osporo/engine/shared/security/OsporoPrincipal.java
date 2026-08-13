package com.osporo.engine.shared.security;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class OsporoPrincipal {

    private final UUID userId;
    private final UUID tenantId;
    private final List<String> permissions;

    public OsporoPrincipal(UUID userId, UUID tenantId, List<String> permissions) {
        this.userId      = userId;
        this.tenantId    = tenantId;
        this.permissions = Collections.unmodifiableList(permissions);
    }

    public UUID getUserId()            { return userId; }
    public UUID getTenantId()          { return tenantId; }
    public List<String> getPermissions() { return permissions; }
}
