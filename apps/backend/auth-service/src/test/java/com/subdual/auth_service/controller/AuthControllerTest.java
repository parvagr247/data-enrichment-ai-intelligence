package com.subdual.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.auth_service.dto.AuthResponse;
import com.subdual.auth_service.dto.LoginRequest;
import com.subdual.auth_service.dto.RegisterRequest;
import com.subdual.auth_service.dto.UserDto;
import com.subdual.auth_service.exception.GlobalExceptionHandler;
import com.subdual.auth_service.exception.InvalidCredentialsException;
import com.subdual.auth_service.exception.UserAlreadyExistsException;
import com.subdual.auth_service.service.AuthService;
import com.subdual.auth_service.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "Password123!");
        UserDto userDto = new UserDto("u-1", "Alice", "alice@example.com", Instant.now());
        AuthResponse response = new AuthResponse("test-jwt-token", userDto);

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.user.id").value("u-1"))
                .andExpect(jsonPath("$.user.email").value("alice@example.com"));
    }

    @Test
    void register_conflictWhenEmailExists() throws Exception {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "Password123!");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new UserAlreadyExistsException("Email is already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123!");
        UserDto userDto = new UserDto("u-1", "Alice", "alice@example.com", Instant.now());
        AuthResponse response = new AuthResponse("test-jwt-token", userDto);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"));
    }

    @Test
    void login_unauthorizedOnInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withXUserIdHeader() throws Exception {
        UserDto userDto = new UserDto("u-1", "Alice", "alice@example.com", Instant.now());

        when(authService.getCurrentUser("u-1")).thenReturn(userDto);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-User-Id", "u-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getMe_unauthorizedWhenNoAuth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
