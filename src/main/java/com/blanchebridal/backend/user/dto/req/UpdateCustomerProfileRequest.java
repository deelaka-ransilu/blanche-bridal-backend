package com.blanchebridal.backend.user.dto.req;

import java.util.List;

public record UpdateCustomerProfileRequest(
        String adminNotes,
        List<String> designImageUrls
) {}