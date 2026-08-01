package com.blanchebridal.backend.appointment.repository;

import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Page<Appointment> findByUser_Id(UUID userId, Pageable pageable);
    Page<Appointment> findByStatus(AppointmentStatus status, Pageable pageable);
    List<Appointment> findByAppointmentDateAndStatusNot(
            LocalDate date, AppointmentStatus status);
    boolean existsByAppointmentDateAndTimeSlotAndStatusNot(
            LocalDate date, String timeSlot, AppointmentStatus status);
    List<Appointment> findByAppointmentDateAndStatus(LocalDate date, AppointmentStatus status);

    @Query("SELECT a FROM Appointment a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.product " +
            "WHERE a.id = :id")
    Optional<Appointment> findByIdWithUserAndProduct(@Param("id") UUID id);
}