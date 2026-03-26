package edu.bridalshop.backend.repository;

import edu.bridalshop.backend.entity.RentalDamageItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RentalDamageItemRepository extends JpaRepository<RentalDamageItem, Integer> {

    List<RentalDamageItem> findAllByRental_RentalId(Integer rentalId);
}