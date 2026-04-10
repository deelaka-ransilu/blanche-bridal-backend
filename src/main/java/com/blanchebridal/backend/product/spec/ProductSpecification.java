package com.blanchebridal.backend.product.spec;

import com.blanchebridal.backend.product.dto.ProductFilters;
import com.blanchebridal.backend.product.entity.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> withFilters(ProductFilters filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filters.type() != null) {
                predicates.add(cb.equal(root.get("type"), filters.type()));
            }
            if (filters.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filters.categoryId()));
            }
            if (filters.available() != null) {
                predicates.add(cb.equal(root.get("isAvailable"), filters.available()));
            }
            if (filters.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("purchasePrice"), filters.minPrice()));
            }
            if (filters.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("purchasePrice"), filters.maxPrice()));
            }
            if (filters.search() != null && !filters.search().isBlank()) {
                String pattern = "%" + filters.search().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}