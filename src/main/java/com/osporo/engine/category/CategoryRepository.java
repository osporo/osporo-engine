package com.osporo.engine.category;

import com.osporo.engine.category.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Transactional
    @Query(value = """
            INSERT INTO categories (tenant_id, name, slug, is_active)
            VALUES (:tenantId, :name, :slug, :active)
            ON CONFLICT (tenant_id, slug) DO NOTHING
            RETURNING id, tenant_id, name, slug, is_active, created_at
            """, nativeQuery = true)
    Optional<Category> insert(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("slug") String slug,
            @Param("active") boolean active
    );
}
