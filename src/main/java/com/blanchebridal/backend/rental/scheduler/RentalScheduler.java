package com.blanchebridal.backend.rental.scheduler;

import com.blanchebridal.backend.rental.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentalScheduler {

    private final RentalService rentalService;

    // Runs every day at 8:00 AM server time
    @Scheduled(cron = "0 0 8 * * *")
    public void detectOverdueRentals() {
        log.info("[RentalScheduler] Running overdue rental detection...");
        rentalService.markOverdueRentals();
    }
}