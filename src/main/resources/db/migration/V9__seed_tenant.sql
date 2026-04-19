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

            RAISE NOTICE 'Seeded default tenant';

        ELSE
            RAISE NOTICE 'Tenants table already has data, skipping seed.';
        END IF;
    END $$;