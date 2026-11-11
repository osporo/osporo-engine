package com.osporo.engine.category;

import com.osporo.engine.category.dto.CategoryResponse;
import com.osporo.engine.category.dto.CreateCategoryRequest;
import com.osporo.engine.category.model.Category;
import com.osporo.engine.shared.response.ApiResponse;
import com.osporo.engine.shared.security.OsporoPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORY_CREATE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal OsporoPrincipal principal,
            @RequestBody @Valid CreateCategoryRequest request
    ) {
        Category category = categoryService.create(principal.getTenantId(), request);
        CategoryResponse response = new CategoryResponse(
                category.getId(), category.getName(), category.getSlug(), category.isActive(), category.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
