DO $$
DECLARE
    v_tenant_id UUID;
BEGIN
    -- Resolve the default tenant
    SELECT id INTO v_tenant_id FROM tenants LIMIT 1;

    IF v_tenant_id IS NULL THEN
        RAISE EXCEPTION 'No tenant found. Run the tenant seed migration first.';
    END IF;

    INSERT INTO tenant_role_config (tenant_id, role_name, permissions, is_default)
    VALUES
        -- BUYER — default role assigned on registration
        (
            v_tenant_id,
            'BUYER',
            ARRAY[
                'LISTING_READ',
                'ORDER_CREATE',
                'ORDER_READ_OWN',
                'MESSAGE_CREATE',
                'MESSAGE_READ_OWN',
                'REPORT_CREATE'
            ],
            true
        ),

        -- SELLER
        (
            v_tenant_id,
            'SELLER',
            ARRAY[
                'LISTING_CREATE',
                'LISTING_READ',
                'LISTING_UPDATE_OWN',
                'LISTING_DELETE_OWN',
                'ORDER_READ_OWN',
                'MESSAGE_CREATE',
                'MESSAGE_READ_OWN',
                'REPORT_CREATE'
            ],
            false
        ),

        -- MARKETPLACE_STAFF
        (
            v_tenant_id,
            'MARKETPLACE_STAFF',
            ARRAY[
                'LISTING_READ',
                'LISTING_TAKEDOWN_ANY',
                'ORDER_READ_ANY',
                'USER_READ_ANY',
                'USER_SUSPEND',
                'MODERATION_REVIEW',
                'REPORT_CREATE'
            ],
            false
        ),

        -- MARKETPLACE_OWNER
        (
            v_tenant_id,
            'MARKETPLACE_OWNER',
            ARRAY[
                'LISTING_READ',
                'LISTING_TAKEDOWN_ANY',
                'ORDER_READ_ANY',
                'ORDER_REFUND',
                'USER_READ_ANY',
                'USER_SUSPEND',
                'USER_INVITE',
                'MODERATION_REVIEW',
                'MODERATION_CONFIG',
                'REPORT_CREATE'
            ],
            false
        ),

        -- OSPORO_ADMIN — platform level, no tenant-scoped permissions
        (
            v_tenant_id,
            'OSPORO_ADMIN',
            ARRAY[
                'PLATFORM_ADMIN'
            ],
            false
        );

    RAISE NOTICE 'Seeded tenant_role_config for tenant: %', v_tenant_id;

END $$;
