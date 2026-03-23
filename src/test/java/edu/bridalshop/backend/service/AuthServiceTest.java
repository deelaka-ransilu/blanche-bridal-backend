package edu.bridalshop.backend.service;

import edu.bridalshop.backend.entity.User;
import edu.bridalshop.backend.entity.UserRole;
import edu.bridalshop.backend.exception.EmailAlreadyExistsException;
import edu.bridalshop.backend.exception.InvalidCredentialsException;
import edu.bridalshop.backend.repository.CustomerProfileRepository;
import edu.bridalshop.backend.repository.RefreshTokenRepository;
import edu.bridalshop.backend.repository.UserRepository;
import edu.bridalshop.backend.security.CustomUserDetails;
import edu.bridalshop.backend.security.GoogleTokenVerifier;
import edu.bridalshop.backend.security.JwtService;
import edu.bridalshop.backend.security.TokenService;
import edu.bridalshop.backend.dto.request.LoginRequest;
import edu.bridalshop.backend.dto.request.RegisterRequest;
import edu.bridalshop.backend.util.PayloadSanitizer;
import edu.bridalshop.backend.util.PublicIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository            userRepository;
    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private RefreshTokenRepository    refreshTokenRepository;
    @Mock private PasswordEncoder           passwordEncoder;
    @Mock private JwtService                jwtService;
    @Mock private PublicIdGenerator         publicIdGenerator;
    @Mock private PayloadSanitizer          sanitizer;
    @Mock private TokenService              tokenService;
    @Mock private EmailService              emailService;
    @Mock private CloudinaryService         cloudinaryService;
    @Mock private GoogleTokenVerifier       googleTokenVerifier;

    @InjectMocks
    private AuthService authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .userId(1)
                .publicId("usr_TestPublicId")
                .fullName("Test User")
                .email("test@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .role(UserRole.CUSTOMER)
                .emailVerified(true)
                .isActive(true)
                .build();

        // Sanitizer returns input unchanged by default
        lenient().when(sanitizer.sanitizeText(anyString()))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(sanitizer.sanitizeEmail(anyString()))
                .thenAnswer(i -> ((String) i.getArgument(0)).toLowerCase().trim());
    }

    // ── Register Tests ─────────────────────────────────────────────────

    @Test
    void register_shouldThrowException_whenEmailAlreadyExists() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("New User");
        req.setEmail("test@example.com");
        req.setPassword("Test@1234");
        req.setPhone("+94771234567");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.register(req));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldSaveUser_whenEmailIsNew() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("New User");
        req.setEmail("newuser@example.com");
        req.setPassword("Test@1234");
        req.setPhone("+94771234567");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(publicIdGenerator.forUser()).thenReturn("usr_NewPublicId");
        when(passwordEncoder.encode(anyString())).thenReturn("hashedpwd");
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(tokenService.generateEmailVerificationToken(any(), anyString()))
                .thenReturn("mock-token");
        doNothing().when(emailService)
                .sendVerificationEmail(anyString(), anyString(), anyString());

        var result = authService.register(req);

        assertNotNull(result);
        assertEquals("Registration successful. Please check your email to verify your account.",
                result.getMessage());
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ── Login Tests ────────────────────────────────────────────────────

    @Test
    void login_shouldThrowException_whenUserNotFound() {
        LoginRequest req = new LoginRequest();
        req.setEmail("notfound@example.com");
        req.setPassword("Test@1234");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(req));
    }

    @Test
    void login_shouldThrowException_whenPasswordIsWrong() {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("WrongPass@1");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(req));
    }

    @Test
    void login_shouldThrowException_whenEmailNotVerified() {
        existingUser.setEmailVerified(false);

        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("Test@1234");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(req));
    }

    @Test
    void login_shouldReturnTokens_whenCredentialsAreValid() {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("Test@1234");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class)))
                .thenReturn("mock-access-token");
        when(refreshTokenRepository.save(any()))
                .thenReturn(null);

        var result = authService.login(req);

        assertNotNull(result);
        assertEquals("mock-access-token", result.getAccessToken());
        assertEquals("CUSTOMER", result.getRole());
    }
}