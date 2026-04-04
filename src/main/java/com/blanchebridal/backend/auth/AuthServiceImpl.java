package com.blanchebridal.backend.auth;

import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.dto.req.GoogleAuthRequest;
import com.blanchebridal.backend.auth.dto.req.LoginRequest;
import com.blanchebridal.backend.auth.dto.req.RegisterRequest;
import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.user.User;
import com.blanchebridal.backend.user.UserRepository;
import com.blanchebridal.backend.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getRole().name());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getRole().name());
    }

    @Override
    public AuthResponse googleAuth(GoogleAuthRequest request) {
        // Google OAuth will be implemented in B10
        throw new UnsupportedOperationException("Google auth not yet implemented");
    }
}