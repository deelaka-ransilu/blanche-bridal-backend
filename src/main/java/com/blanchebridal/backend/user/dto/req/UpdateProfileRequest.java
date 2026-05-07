package com.blanchebridal.backend.user.dto.req;

public record UpdateProfileRequest(
        String firstName,
        String lastName,
        String phone,
        String address
) {}