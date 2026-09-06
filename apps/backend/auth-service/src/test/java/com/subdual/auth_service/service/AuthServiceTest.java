package com.subdual.auth_service.service;

import com.subdual.auth_service.domain.User;
import com.subdual.auth_service.dto.AuthResponse;
import com.subdual.auth_service.dto.LoginRequest;
import com.subdual.auth_service.dto.RegisterRequest;
import com.subdual.auth_service.dto.UserDto;
import com.subdual.auth_service.exception.InvalidCredentialsException;
import com.subdual.auth_service.exception.ResourceNotFoundException;
import com.subdual.auth_service.exception.UserAlreadyExistsException;
import com.subdual.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private DefaultAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new DefaultAuthService(userRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("Alice Tester", "alice@example.com", "Secret123!");

        User savedUser = User.builder()
                .id("user-123")
                .name("Alice Tester")
                .email("alice@example.com")
                .passwordHash("hashed-pw")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenService.generateToken(savedUser)).thenReturn("jwt.token.alice");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt.token.alice");
        assertThat(response.user().id()).isEqualTo("user-123");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        assertThat(response.user().name()).isEqualTo("Alice Tester");

        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsWhenEmailExists() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "Secret123!");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("alice@example.com", "Secret123!");

        User user = User.builder()
                .id("user-123")
                .name("Alice Tester")
                .email("alice@example.com")
                .passwordHash("hashed-pw")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123!", "hashed-pw")).thenReturn(true);
        when(jwtTokenService.generateToken(user)).thenReturn("jwt.token.alice");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt.token.alice");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
    }

    @Test
    void login_throwsWhenUserNotFound() {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "Password123!");

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsWhenPasswordWrong() {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword");

        User user = User.builder()
                .id("user-123")
                .email("alice@example.com")
                .passwordHash("hashed-pw")
                .enabled(true)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsWhenAccountDisabled() {
        LoginRequest request = new LoginRequest("disabled@example.com", "Secret123!");

        User user = User.builder()
                .id("user-disabled")
                .email("disabled@example.com")
                .passwordHash("hashed-pw")
                .enabled(false)
                .build();

        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123!", "hashed-pw")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("User account is disabled");
    }

    @Test
    void getCurrentUser_success() {
        User user = User.builder()
                .id("user-123")
                .name("Alice")
                .email("alice@example.com")
                .createdAt(Instant.now())
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        UserDto dto = authService.getCurrentUser("user-123");
        assertThat(dto.id()).isEqualTo("user-123");
        assertThat(dto.email()).isEqualTo("alice@example.com");
    }

    @Test
    void getCurrentUser_throwsWhenNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
