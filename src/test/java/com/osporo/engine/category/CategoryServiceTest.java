package com.osporo.engine.category;

import com.osporo.engine.category.dto.CategoryListQuery;
import com.osporo.engine.category.dto.CreateCategoryRequest;
import com.osporo.engine.category.model.Category;
import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.exception.InsufficientPermissionsException;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.shared.exception.RequestValidationException;
import com.osporo.engine.shared.security.TenantContextHolder;
import com.osporo.engine.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantContextHolder tenantContextHolder;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void list_shouldRejectGuestAccessToInactiveCategoriesBeforeQuerying() {
        CategoryListQuery query = new CategoryListQuery(1, 20, CategoryListQuery.Status.INACTIVE);

        assertThatThrownBy(() -> categoryService.list(null, query))
                .isInstanceOf(InsufficientPermissionsException.class);
        verifyNoInteractions(categoryRepository, tenantRepository, tenantContextHolder);
    }

    @Test
    void create_shouldNormalizeSlugAndPreserveExplicitInactiveStatus() {
        UUID tenantId = UUID.randomUUID();
        Category category = new Category(
                UUID.randomUUID(), tenantId, "CAFÉ & 家電", "café-家電", false, OffsetDateTime.now()
        );
        when(categoryRepository.insert(tenantId, "CAFÉ & 家電", "café-家電", false))
                .thenReturn(Optional.of(category));

        assertThat(categoryService.create(tenantId, new CreateCategoryRequest("  CAFÉ & 家電  ", false)))
                .isSameAs(category);
    }

    @Test
    void create_shouldRejectNamesThatCannotProduceASlug() {
        assertThatThrownBy(() -> categoryService.create(UUID.randomUUID(), new CreateCategoryRequest("!!", null)))
                .isInstanceOf(RequestValidationException.class);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void create_shouldMapAnExistingSlugToAConflict() {
        UUID tenantId = UUID.randomUUID();
        when(categoryRepository.insert(tenantId, "Electronics", "electronics", true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(tenantId, new CreateCategoryRequest("Electronics", null)))
                .isInstanceOfSatisfying(OsporoException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_SLUG_EXISTS));
    }
}
