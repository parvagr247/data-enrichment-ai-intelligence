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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAuthService implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public DefaultAuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        log.info("Attempting to register user with email: {}", normalizedEmail);

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected: email {} is already registered", normalizedEmail);
            throw new UserAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        String token = jwtTokenService.generateToken(savedUser);
        return new AuthResponse(token, toUserDto(savedUser));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        log.info("Attempting login for email: {}", normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: email {} not found", normalizedEmail);
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: password mismatch for email {}", normalizedEmail);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            log.warn("Login failed: account is disabled for email {}", normalizedEmail);
            throw new InvalidCredentialsException("User account is disabled");
        }

        log.info("User {} logged in successfully", user.getId());
        String token = jwtTokenService.generateToken(user);
        return new AuthResponse(token, toUserDto(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return toUserDto(user);
    }

    private UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
