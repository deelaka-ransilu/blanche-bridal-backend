package com.blanchebridal.backend.product.dto.req;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateCategoryRequest(
        @Size(max = 100) String name,
        @Size(max = 100) String slug,
        UUID parentId
) {}