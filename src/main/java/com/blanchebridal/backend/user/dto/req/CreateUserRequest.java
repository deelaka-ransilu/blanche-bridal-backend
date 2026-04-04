package com.blanchebridal.backend.user.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phone
) {}