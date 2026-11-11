package com.osporo.engine.category;

import com.osporo.engine.category.dto.CreateCategoryRequest;
import com.osporo.engine.category.model.Category;
import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.exception.OsporoException;
import com.osporo.engine.shared.exception.RequestValidationException;
import com.osporo.engine.shared.response.ErrorDetail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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
