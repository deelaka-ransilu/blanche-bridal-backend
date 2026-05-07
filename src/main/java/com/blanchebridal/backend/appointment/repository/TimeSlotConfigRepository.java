package com.blanchebridal.backend.appointment.repository;

import com.blanchebridal.backend.appointment.entity.TimeSlotConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimeSlotConfigRepository extends JpaRepository<TimeSlotConfig, UUID> {
    List<TimeSlotConfig> findByDayOfWeekAndIsActiveTrue(Integer dayOfWeek);
}