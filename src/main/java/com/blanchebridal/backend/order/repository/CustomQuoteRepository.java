package com.blanchebridal.backend.order.repository;

import com.blanchebridal.backend.order.entity.CustomQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomQuoteRepository extends JpaRepository<CustomQuote, UUID> {

    List<CustomQuote> findByCustomDesignRequest_IdOrderByVersionDesc(UUID customDesignRequestId);

    // Convenience for createQuote()'s "already has a pending quote?" check
    // and getLatestQuote() — avoids pulling the full history just to peek
    // at the newest row.
    default CustomQuote findLatestOrNull(UUID customDesignRequestId) {
        List<CustomQuote> quotes = findByCustomDesignRequest_IdOrderByVersionDesc(customDesignRequestId);
        return quotes.isEmpty() ? null : quotes.get(0);
    }
}