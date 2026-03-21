package edu.bridalshop.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String publicId;
    private String fullName;
    private String email;
    private String role;
    private Boolean emailVerified;
    private Boolean profileCompleted;
}