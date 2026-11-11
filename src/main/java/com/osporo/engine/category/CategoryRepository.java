package com.osporo.engine.category;

import com.osporo.engine.category.model.Category;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CategoryRepository {

    private final JdbcClient jdbcClient;

    public CategoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Category> insert(UUID tenantId, String name, String slug, boolean active) {
        return jdbcClient.sql("""
                INSERT INTO categories (tenant_id, name, slug, is_active)
                VALUES (:tenantId, :name, :slug, :active)
                ON CONFLICT (tenant_id, slug) DO NOTHING
                RETURNING id, name, slug, is_active AS active, created_at
                """)
                .param("tenantId", tenantId)
                .param("name", name)
                .param("slug", slug)
                .param("active", active)
                .query(Category.class)
                .optional();
    }
}
