package com.blanchebridal.backend.product.dto.req;

import com.blanchebridal.backend.product.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String slug,
        UUID parentId,
        @NotNull CategoryType type
) {}