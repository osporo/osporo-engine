package com.osporo.engine.category;

import com.osporo.engine.category.dto.CategoryListQuery;
import com.osporo.engine.category.dto.CreateCategoryRequest;
import com.osporo.engine.category.model.Category;
import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.enums.Permission;
import com.osporo.engine.shared.enums.TenantStatus;
import com.osporo.engine.shared.exception.InsufficientPermissionsException;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.shared.exception.RequestValidationException;
import com.osporo.engine.shared.exception.TenantNotFoundException;
import com.osporo.engine.shared.exception.TenantSuspendedException;
import com.osporo.engine.shared.response.ErrorDetail;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.tenant.TenantRepository;
import com.osporo.engine.tenant.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;
    private final TenantContextHolder tenantContextHolder;

    public CategoryService(
            CategoryRepository categoryRepository,
            TenantRepository tenantRepository,
            TenantContextHolder tenantContextHolder
    ) {
        this.categoryRepository = categoryRepository;
        this.tenantRepository = tenantRepository;
        this.tenantContextHolder = tenantContextHolder;
    }

    @Transactional(readOnly = true)
    public Page<Category> list(OsporoPrincipal principal, CategoryListQuery query) {
        if (query.status() != CategoryListQuery.Status.ACTIVE && !canViewInactive(principal)) {
            throw new InsufficientPermissionsException();
        }

        UUID tenantId = principal == null ? tenantContextHolder.getTenantId() : principal.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException(tenantId);
        }

        PageRequest page = PageRequest.of(query.page() - 1, query.perPage(), Sort.by("name", "id"));
        boolean allStatuses = query.status() == CategoryListQuery.Status.ALL;
        boolean active = query.status() == CategoryListQuery.Status.ACTIVE;

        if (page.getOffset() > Integer.MAX_VALUE) {
            long total = allStatuses ? categoryRepository.countByTenantId(tenantId)
                    : categoryRepository.countByTenantIdAndActive(tenantId, active);
            return new PageImpl<>(List.of(), page, total);
        }

        return allStatuses ? categoryRepository.findAllByTenantId(tenantId, page)
                : categoryRepository.findAllByTenantIdAndActive(tenantId, active, page);
    }

    private boolean canViewInactive(OsporoPrincipal principal) {
        return principal != null && (principal.getPermissions().contains(Permission.CATEGORY_CREATE.name())
                || principal.getPermissions().contains(Permission.CATEGORY_EDIT.name()));
    }

    public Category create(UUID tenantId, CreateCategoryRequest request) {
        String slug = Normalizer.normalize(request.name().toLowerCase(Locale.ROOT), Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{Nd}\\p{M}]+", "-")
                .replaceAll("^-|-$", "");

        if (slug.codePoints().noneMatch(Character::isLetterOrDigit) || slug.codePointCount(0, slug.length()) > 120) {
            throw new RequestValidationException(List.of(
                    new ErrorDetail("name", "Must produce a slug of at most 120 characters containing a letter or digit.")
            ));
        }

        boolean active = !Boolean.FALSE.equals(request.active());

        return categoryRepository.insert(tenantId, request.name(), slug, active)
                .orElseThrow(() -> new OsporoException(
                        ErrorCode.CATEGORY_SLUG_EXISTS, HttpStatus.CONFLICT,
                        "A category with this slug already exists in the tenant."
                ));
    }
}
