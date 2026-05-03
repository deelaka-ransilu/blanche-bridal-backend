package com.blanchebridal.backend.product;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.dto.req.CreateCategoryRequest;
import com.blanchebridal.backend.product.dto.req.UpdateCategoryRequest;
import com.blanchebridal.backend.product.dto.res.CategoryResponse;
import com.blanchebridal.backend.product.entity.Category;
import com.blanchebridal.backend.product.repository.CategoryRepository;
import com.blanchebridal.backend.product.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category parentCategory;
    private Category childCategory;
    private UUID parentId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        parentId = UUID.randomUUID();
        childId  = UUID.randomUUID();

        parentCategory = Category.builder()
                .id(parentId)
                .name("Bridal Gowns")
                .slug("bridal-gowns")
                .parent(null)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        childCategory = Category.builder()
                .id(childId)
                .name("Ball Gowns")
                .slug("ball-gowns")
                .parent(parentCategory)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET ALL CATEGORIES
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAllCategories: returns all categories including children")
    void getAllCategories_returnsAll() {
        when(categoryRepository.findAllByIsActiveTrue())
                .thenReturn(List.of(parentCategory, childCategory));

        List<CategoryResponse> result = categoryService.getAllCategories();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Bridal Gowns");
        assertThat(result.get(1).name()).isEqualTo("Ball Gowns");
    }

    @Test
    @DisplayName("getAllCategories: returns empty list when no categories exist")
    void getAllCategories_empty_returnsEmptyList() {
        when(categoryRepository.findAllByIsActiveTrue()).thenReturn(List.of());

        List<CategoryResponse> result = categoryService.getAllCategories();

        assertThat(result).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET CATEGORY BY ID
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCategoryById: success — returns correct category")
    void getCategoryById_success() {
        when(categoryRepository.findByIdAndIsActiveTrue(parentId))
                .thenReturn(Optional.of(parentCategory));

        CategoryResponse result = categoryService.getCategoryById(parentId);

        assertThat(result.id()).isEqualTo(parentId);
        assertThat(result.name()).isEqualTo("Bridal Gowns");
        assertThat(result.slug()).isEqualTo("bridal-gowns");
        assertThat(result.parentId()).isNull();
        assertThat(result.parentName()).isNull();
    }

    @Test
    @DisplayName("getCategoryById: child — response includes parentId and parentName")
    void getCategoryById_child_includesParentInfo() {
        when(categoryRepository.findByIdAndIsActiveTrue(childId))
                .thenReturn(Optional.of(childCategory));

        CategoryResponse result = categoryService.getCategoryById(childId);

        assertThat(result.name()).isEqualTo("Ball Gowns");
        assertThat(result.parentId()).isEqualTo(parentId);
        assertThat(result.parentName()).isEqualTo("Bridal Gowns");
    }

    @Test
    @DisplayName("getCategoryById: fail — unknown id throws ResourceNotFoundException")
    void getCategoryById_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(categoryRepository.findByIdAndIsActiveTrue(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CREATE CATEGORY
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createCategory: success — top-level category with no parent")
    void createCategory_topLevel_success() {
        when(categoryRepository.existsBySlug("veils")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(
                Category.builder()
                        .id(UUID.randomUUID())
                        .name("Veils")
                        .slug("veils")
                        .parent(null)
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        CreateCategoryRequest request = new CreateCategoryRequest("Veils", "veils", null);
        CategoryResponse result = categoryService.createCategory(request);

        assertThat(result.name()).isEqualTo("Veils");
        assertThat(result.slug()).isEqualTo("veils");
        assertThat(result.parentId()).isNull();
    }

    @Test
    @DisplayName("createCategory: success — child category with valid parentId")
    void createCategory_withParent_success() {
        when(categoryRepository.existsBySlug("ball-gowns")).thenReturn(false);
        when(categoryRepository.findByIdAndIsActiveTrue(parentId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(childCategory);

        CreateCategoryRequest request = new CreateCategoryRequest(
                "Ball Gowns", "ball-gowns", parentId);
        CategoryResponse result = categoryService.createCategory(request);

        assertThat(result.parentId()).isEqualTo(parentId);
        assertThat(result.parentName()).isEqualTo("Bridal Gowns");
    }

    @Test
    @DisplayName("createCategory: fail — duplicate slug throws ConflictException")
    void createCategory_duplicateSlug_throwsConflict() {
        when(categoryRepository.existsBySlug("bridal-gowns")).thenReturn(true);

        CreateCategoryRequest request = new CreateCategoryRequest(
                "Bridal Gowns", "bridal-gowns", null);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Slug already in use");
    }

    @Test
    @DisplayName("createCategory: fail — parentId not found throws ResourceNotFoundException")
    void createCategory_parentNotFound_throwsException() {
        UUID unknownParentId = UUID.randomUUID();
        when(categoryRepository.existsBySlug("mini-gowns")).thenReturn(false);
        when(categoryRepository.findByIdAndIsActiveTrue(unknownParentId)).thenReturn(Optional.empty());

        CreateCategoryRequest request = new CreateCategoryRequest(
                "Mini Gowns", "mini-gowns", unknownParentId);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UPDATE CATEGORY
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateCategory: success — updates name and slug")
    void updateCategory_updateNameAndSlug_success() {
        when(categoryRepository.findByIdAndIsActiveTrue(parentId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.existsBySlug("wedding-gowns")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Wedding Gowns", "wedding-gowns", null);
        CategoryResponse result = categoryService.updateCategory(parentId, request);

        assertThat(result.name()).isEqualTo("Wedding Gowns");
        assertThat(result.slug()).isEqualTo("wedding-gowns");
    }

    @Test
    @DisplayName("updateCategory: success — same slug does not trigger conflict check")
    void updateCategory_sameSlug_noConflict() {
        when(categoryRepository.findByIdAndIsActiveTrue(parentId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        // Passing the same slug that already exists on this category — should not throw
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Bridal Gowns", "bridal-gowns", null);

        CategoryResponse result = categoryService.updateCategory(parentId, request);

        assertThat(result.slug()).isEqualTo("bridal-gowns");
        // existsBySlug should never be called since the slug didn't change
        verify(categoryRepository, never()).existsBySlug(any());
    }

    @Test
    @DisplayName("updateCategory: fail — new slug already taken throws ConflictException")
    void updateCategory_newSlugTaken_throwsConflict() {
        when(categoryRepository.findByIdAndIsActiveTrue(parentId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.existsBySlug("ball-gowns")).thenReturn(true);

        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Ball Gowns", "ball-gowns", null);

        assertThatThrownBy(() -> categoryService.updateCategory(parentId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Slug already in use");
    }

    @Test
    @DisplayName("updateCategory: fail — category pointing to itself as parent throws ConflictException")
    void updateCategory_selfParent_throwsConflict() {
        when(categoryRepository.findByIdAndIsActiveTrue(parentId)).thenReturn(Optional.of(parentCategory));

        // Trying to set a category's own ID as its parentId
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Bridal Gowns", "bridal-gowns", parentId);

        assertThatThrownBy(() -> categoryService.updateCategory(parentId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be its own parent");
    }

    @Test
    @DisplayName("updateCategory: fail — unknown id throws ResourceNotFoundException")
    void updateCategory_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(categoryRepository.findByIdAndIsActiveTrue(unknownId)).thenReturn(Optional.empty());

        UpdateCategoryRequest request = new UpdateCategoryRequest("X", "x", null);

        assertThatThrownBy(() -> categoryService.updateCategory(unknownId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DELETE CATEGORY
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteCategory: success — marks category inactive")
    void deleteCategory_success() {
        when(categoryRepository.findByIdAndIsActiveTrue(parentId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.deleteCategory(parentId);

        verify(categoryRepository, times(1)).save(any(Category.class));
        assertThat(parentCategory.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("deleteCategory: fail — unknown id throws ResourceNotFoundException")
    void deleteCategory_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(categoryRepository.findByIdAndIsActiveTrue(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(categoryRepository, never()).save(any());
    }
}