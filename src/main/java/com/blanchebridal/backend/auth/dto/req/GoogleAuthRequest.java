package com.blanchebridal.backend.auth.dto.req;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank String googleToken
) {}