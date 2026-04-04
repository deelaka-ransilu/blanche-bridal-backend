package com.blanchebridal.backend.auth;

import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.dto.req.GoogleAuthRequest;
import com.blanchebridal.backend.auth.dto.req.LoginRequest;
import com.blanchebridal.backend.auth.dto.req.RegisterRequest;
import com.blanchebridal.backend.user.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleAuth(
            @Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.googleAuth(request);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    @PostMapping("/setup-superadmin")
    public ResponseEntity<Map<String, Object>> setupSuperadmin(
            @Valid @RequestBody RegisterRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new com.blanchebridal.backend.exception.ConflictException("Superadmin already exists");
        }

        com.blanchebridal.backend.user.User user = com.blanchebridal.backend.user.User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(com.blanchebridal.backend.user.UserRole.SUPERADMIN)
                .isActive(true)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(Map.of("success", true, "data",
                new AuthResponse(token, user.getRole().name())));
    }
}