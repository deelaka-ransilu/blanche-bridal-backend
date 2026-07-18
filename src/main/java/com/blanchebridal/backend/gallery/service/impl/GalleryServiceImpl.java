package com.blanchebridal.backend.gallery.service.impl;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.gallery.dto.req.CreateGalleryImageRequest;
import com.blanchebridal.backend.gallery.dto.req.UpdateGalleryImageRequest;
import com.blanchebridal.backend.gallery.dto.res.GalleryImageResponse;
import com.blanchebridal.backend.gallery.entity.GalleryImage;
import com.blanchebridal.backend.gallery.repository.GalleryImageRepository;
import com.blanchebridal.backend.gallery.service.GalleryService;
import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GalleryServiceImpl implements GalleryService {

    private final GalleryImageRepository galleryImageRepository;
    private final Cloudinary cloudinary;

    @Override
    @Transactional(readOnly = true)
    public List<GalleryImageResponse> getAllImages() {
        return galleryImageRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public GalleryImageResponse createImage(CreateGalleryImageRequest request) {
        GalleryImage image = GalleryImage.builder()
                .url(request.getUrl())
                .publicId(request.getPublicId())
                .caption(request.getCaption())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        return toResponse(galleryImageRepository.save(image));
    }

    @Override
    @Transactional
    public GalleryImageResponse updateImage(UUID id, UpdateGalleryImageRequest request) {
        GalleryImage image = findActiveById(id);

        if (request.getCaption() != null) image.setCaption(request.getCaption());
        if (request.getDisplayOrder() != null) image.setDisplayOrder(request.getDisplayOrder());

        return toResponse(galleryImageRepository.save(image));
    }

    @Override
    @Transactional
    public void deleteImage(UUID id) {
        GalleryImage image = findActiveById(id);

        if (image.getPublicId() != null && !image.getPublicId().isBlank()) {
            try {
                cloudinary.uploader().destroy(image.getPublicId(), Map.of());
            } catch (IOException e) {
                // Don't fail the DB operation over a Cloudinary cleanup failure —
                // same non-blocking pattern as ProductServiceImpl.
                log.warn("[Gallery] Cloudinary destroy failed for publicId {}: {}",
                        image.getPublicId(), e.getMessage());
            }
        }

        image.setIsActive(false);
        galleryImageRepository.save(image);
    }

    private GalleryImage findActiveById(UUID id) {
        return galleryImageRepository.findById(id)
                .filter(img -> Boolean.TRUE.equals(img.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Gallery image not found: " + id));
    }

    private GalleryImageResponse toResponse(GalleryImage image) {
        return GalleryImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .caption(image.getCaption())
                .displayOrder(image.getDisplayOrder())
                .createdAt(image.getCreatedAt())
                .build();
    }
}