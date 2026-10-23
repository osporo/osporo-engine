package com.osporo.engine.user;

import com.osporo.engine.user.dto.UserDirectoryQuery;
import com.osporo.engine.user.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

public class UserDirectoryRepositoryImpl implements UserDirectoryRepository {

    private final EntityManager entityManager;

    public UserDirectoryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<User> findDirectory(UUID tenantId, UserDirectoryQuery filters) {
        String predicates = buildPredicates(filters);
        String sortColumn = switch (filters.sort()) {
            case "CREATED_AT" -> "u.created_at";
            case "DELETED_AT" -> "u.deleted_at";
            case "UPDATED_AT" -> "u.updated_at";
            case "SUSPENDED_AT" -> "u.suspended_at";
            default -> throw new IllegalArgumentException("Unsupported directory sort field.");
        };

        PageRequest page = PageRequest.of(filters.page() - 1, filters.perPage());
        Query resultsQuery = entityManager.createNativeQuery(
                "SELECT u.*" + predicates
                        + " ORDER BY " + sortColumn + " " + filters.order() + " NULLS LAST, u.id ASC"
                        + " LIMIT :limit OFFSET :offset",
                User.class
        );
        bindPredicates(resultsQuery, tenantId, filters);
        resultsQuery.setParameter("limit", filters.perPage());
        resultsQuery.setParameter("offset", page.getOffset());

        Query countQuery = entityManager.createNativeQuery("SELECT count(*)" + predicates);
        bindPredicates(countQuery, tenantId, filters);

        List<User> users = resultsQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(users, page, total);
    }

    private String buildPredicates(UserDirectoryQuery filters) {
        StringJoiner roles = new StringJoiner(" OR ", "(", ")");

        for (int index = 0; index < filters.roles().size(); index++) {
            roles.add(":role" + index + " = ANY(u.roles)");
        }

        String status = switch (filters.status()) {
            case null -> "u.deleted_at IS NULL";
            case "ACTIVE" -> "u.deleted_at IS NULL AND u.suspended_at IS NULL";
            case "SUSPENDED" -> "u.deleted_at IS NULL AND u.suspended_at IS NOT NULL";
            case "DELETED" -> "u.deleted_at IS NOT NULL";
            case "ALL" -> "TRUE";
            default -> throw new IllegalArgumentException("Unsupported directory status.");
        };

        return " FROM users u WHERE u.tenant_id = :tenantId"
                + " AND NOT ('OSPORO_ADMIN' = ANY(u.roles))"
                + " AND " + roles
                + " AND " + status;
    }

    private void bindPredicates(Query query, UUID tenantId, UserDirectoryQuery filters) {
        query.setParameter("tenantId", tenantId);

        for (int index = 0; index < filters.roles().size(); index++) {
            query.setParameter("role" + index, filters.roles().get(index).name());
        }
    }
}
