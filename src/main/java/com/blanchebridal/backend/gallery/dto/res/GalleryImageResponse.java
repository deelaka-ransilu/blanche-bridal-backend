package com.blanchebridal.backend.gallery.dto.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GalleryImageResponse {

    private UUID id;
    private String url;
    private String caption;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}