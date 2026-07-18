package com.blanchebridal.backend.gallery.dto.req;

import lombok.Data;

// Same "null means unchanged" convention as UpdateProductRequest /
// UpdateCategoryRequest — caption/order only, no re-upload via this endpoint.
@Data
public class UpdateGalleryImageRequest {

    private String caption;
    private Integer displayOrder;
}