package com.blanchebridal.backend.gallery.repository;

import com.blanchebridal.backend.gallery.entity.GalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {

    List<GalleryImage> findByIsActiveTrueOrderByDisplayOrderAsc();
}