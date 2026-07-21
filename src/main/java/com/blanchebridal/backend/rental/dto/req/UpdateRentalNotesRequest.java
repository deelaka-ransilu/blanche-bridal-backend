package com.blanchebridal.backend.rental.dto.req;

import lombok.Data;

@Data
public class UpdateRentalNotesRequest {

    // No @NotNull -- an empty string is how the admin clears notes.
    private String notes;
}