package edu.bridalshop.backend.scheduler;

import edu.bridalshop.backend.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentalOverdueScheduler {

    private final RentalService rentalService;

    /**
     * Runs every hour.
     * Finds all HANDED_OVER rentals past their due_date and flips them to OVERDUE.
     * cron = "0 0 * * * *" → at the start of every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void flagOverdueRentals() {
        log.info("Running overdue rental check...");
        int count = rentalService.markOverdueRentals();
        if (count > 0) {
            log.warn("Marked {} rental(s) as OVERDUE", count);
        } else {
            log.info("No overdue rentals found");
        }
    }
}