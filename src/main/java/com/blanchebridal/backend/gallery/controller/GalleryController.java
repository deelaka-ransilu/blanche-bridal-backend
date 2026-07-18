package com.blanchebridal.backend.gallery.controller;

import com.blanchebridal.backend.gallery.dto.req.CreateGalleryImageRequest;
import com.blanchebridal.backend.gallery.dto.req.UpdateGalleryImageRequest;
import com.blanchebridal.backend.gallery.service.GalleryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", galleryService.getAllImages()
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateGalleryImageRequest request) {
        log.info("[Gallery] Create image → url: {}", request.getUrl());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", galleryService.createImage(request)
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGalleryImageRequest request) {
        log.info("[Gallery] Update image → id: {}", id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", galleryService.updateImage(id, request)
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        log.info("[Gallery] Delete image → id: {}", id);
        galleryService.deleteImage(id);
        return ResponseEntity.ok(Map.of("success", true, "data", "Image deleted"));
    }
}