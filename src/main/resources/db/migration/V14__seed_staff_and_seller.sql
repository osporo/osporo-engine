INSERT INTO users (tenant_id, email, password_hash, roles, updated_at)
SELECT tenant.id,
       account.email,
       crypt('OsporoLocalOwner123!', gen_salt('bf', 10)),
       ARRAY[account.role_name],
       now()
FROM tenants AS tenant
CROSS JOIN (VALUES
    ('staff@osporo.example', 'MARKETPLACE_STAFF'),
    ('seller@osporo.example', 'SELLER')
) AS account(email, role_name)
WHERE tenant.slug = 'default-marketplace'
ON CONFLICT (tenant_id, email) DO NOTHING;
