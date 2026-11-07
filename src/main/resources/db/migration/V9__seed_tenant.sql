DO $$
    DECLARE
        v_tenant_id UUID;
    BEGIN
        -- Only seed if no tenants exist
        IF NOT EXISTS (SELECT 1 FROM tenants LIMIT 1) THEN

            v_tenant_id := gen_random_uuid();

            INSERT INTO tenants (id, name, slug, status, plan, created_at)
            VALUES (
                       v_tenant_id,
                       'Default Marketplace',
                       'default-marketplace',
                       'ACTIVE',
                       'STARTER',
                       now()
                   );

            INSERT INTO users (tenant_id, email, password_hash, roles, updated_at)
            VALUES (
                       v_tenant_id,
                       'owner@osporo.example',
                       crypt('OsporoLocalOwner123!', gen_salt('bf', 10)),
                       ARRAY['MARKETPLACE_OWNER'],
                       now()
                   );

            RAISE NOTICE 'Seeded default tenant and marketplace owner';

        ELSE
            RAISE NOTICE 'Tenants table already has data, skipping seed.';
        END IF;
    END $$;
