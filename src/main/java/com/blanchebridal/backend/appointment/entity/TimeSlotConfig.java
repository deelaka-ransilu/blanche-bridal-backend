package com.blanchebridal.backend.appointment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "time_slot_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "slot_time", nullable = false, length = 10)
    private String slotTime;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}