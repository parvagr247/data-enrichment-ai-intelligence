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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAuthService implements AuthService {

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

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtTokenService.generateToken(savedUser);
        return new AuthResponse(token, toUserDto(savedUser));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("User account is disabled");
        }

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
