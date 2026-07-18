package com.blanchebridal.backend.gallery.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateGalleryImageRequest {

    @NotBlank(message = "url is required")
    private String url;

    private String publicId;
    private String caption;
    private Integer displayOrder;
}