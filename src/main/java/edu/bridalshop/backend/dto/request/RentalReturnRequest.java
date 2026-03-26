package edu.bridalshop.backend.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record RentalReturnRequest(

        @Size(max = 2000, message = "Return notes must be at most 2000 characters")
        String returnNotes,

        // Optional damage items — empty list or null means clean return
        List<DamageItemRequest> damageItems
) {}