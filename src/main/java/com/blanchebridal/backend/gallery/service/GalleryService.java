package com.blanchebridal.backend.gallery.service;

import com.blanchebridal.backend.gallery.dto.req.CreateGalleryImageRequest;
import com.blanchebridal.backend.gallery.dto.req.UpdateGalleryImageRequest;
import com.blanchebridal.backend.gallery.dto.res.GalleryImageResponse;

import java.util.List;
import java.util.UUID;

public interface GalleryService {

    List<GalleryImageResponse> getAllImages();

    GalleryImageResponse createImage(CreateGalleryImageRequest request);

    GalleryImageResponse updateImage(UUID id, UpdateGalleryImageRequest request);

    void deleteImage(UUID id);
}