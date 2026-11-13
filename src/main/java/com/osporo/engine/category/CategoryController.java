package com.osporo.engine.category;

import com.osporo.engine.category.dto.CategoryListQuery;
import com.osporo.engine.category.dto.CategoryResponse;
import com.osporo.engine.category.dto.CreateCategoryRequest;
import com.osporo.engine.category.model.Category;
import com.osporo.engine.shared.response.ApiResponse;
import com.osporo.engine.shared.response.Meta;
import com.osporo.engine.shared.security.OsporoPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> listCategories(
            @AuthenticationPrincipal OsporoPrincipal principal,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        CategoryListQuery query = CategoryListQuery.from(parameters);
        Page<CategoryResponse> categories = categoryService.list(principal, query).map(this::toResponse);

        return ApiResponse.of(categories.getContent(), Meta.paginated(categories));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORY_CREATE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal OsporoPrincipal principal,
            @RequestBody @Valid CreateCategoryRequest request
    ) {
        Category category = categoryService.create(principal.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toResponse(category)));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getSlug(), category.isActive(), category.getCreatedAt()
        );
    }
}
