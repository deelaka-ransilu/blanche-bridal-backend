package edu.bridalshop.backend.repository;

import edu.bridalshop.backend.entity.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RentalRepository extends JpaRepository<Rental, Integer> {

    Optional<Rental> findByPublicId(String publicId);

    // All rentals — newest first
    List<Rental> findAllByOrderByCreatedAtDesc();

    // Overdue: handed over but past due date and not yet returned
    @Query("""
        SELECT r FROM Rental r
        WHERE r.status = 'HANDED_OVER'
          AND r.dueDate < :today
        ORDER BY r.dueDate ASC
        """)
    List<Rental> findOverdue(LocalDate today);

    // Check if a dress is currently handed over (unavailable for new rental)
    boolean existsByDress_DressIdAndStatus(Integer dressId, String status);
}