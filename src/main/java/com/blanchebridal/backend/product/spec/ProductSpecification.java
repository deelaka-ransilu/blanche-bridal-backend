package com.blanchebridal.backend.product.spec;

import com.blanchebridal.backend.product.dto.ProductFilters;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.rental.entity.Rental;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductSpecification {

    public static Specification<Product> withFilters(ProductFilters filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isTrue(root.get("isActive")));

            if (filters.type() != null) {
                predicates.add(cb.equal(root.get("type"), filters.type()));
            }
            if (filters.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filters.categoryId()));
            }
            if (filters.available() != null) {
                predicates.add(cb.equal(root.get("isAvailable"), filters.available()));

                // Additionally exclude products currently out on an ACTIVE/OVERDUE
                // rental when the caller asked for available=true. isAvailable is a
                // separate, admin-controlled flag (browsability/discontinued/etc.) —
                // this does NOT replace it, it narrows it further using live rental
                // state instead of duplicating that state onto the Product row.
                if (Boolean.TRUE.equals(filters.available())) {
                    Subquery<UUID> rentalSubquery = query.subquery(UUID.class);
                    var rentalRoot = rentalSubquery.from(Rental.class);
                    rentalSubquery.select(rentalRoot.get("product").get("id"))
                            .where(cb.and(
                                    cb.equal(rentalRoot.get("product").get("id"), root.get("id")),
                                    rentalRoot.get("status").in(RentalStatus.ACTIVE, RentalStatus.OVERDUE)
                            ));
                    predicates.add(cb.not(cb.exists(rentalSubquery)));
                }
            }
            if (filters.minPrice() != null || filters.maxPrice() != null) {
                jakarta.persistence.criteria.Expression<java.math.BigDecimal> effectivePrice =
                        cb.coalesce(root.get("rentalPrice"), root.get("purchasePrice"));

                if (filters.minPrice() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(effectivePrice, filters.minPrice()));
                }
                if (filters.maxPrice() != null) {
                    predicates.add(cb.lessThanOrEqualTo(effectivePrice, filters.maxPrice()));
                }
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