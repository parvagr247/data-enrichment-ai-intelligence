package com.subdual.auth_service.service;

import com.subdual.auth_service.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(
                "enrichment-platform-super-secret-jwt-signing-key-256-bits-minimum-required",
                3600000L
        );
    }

    @Test
    void tokenLifecycle_generatesAndExtractsValidClaims() {
        User user = User.builder()
                .id("test-uuid-456")
                .name("Bob Builder")
                .email("bob@builder.com")
                .build();

        String token = jwtTokenService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenService.isTokenValid(token)).isTrue();
        assertThat(jwtTokenService.extractUserId(token)).isEqualTo("test-uuid-456");
        assertThat(jwtTokenService.extractEmail(token)).isEqualTo("bob@builder.com");
    }

    @Test
    void isTokenValid_returnsFalseOnInvalidToken() {
        assertThat(jwtTokenService.isTokenValid("invalid.token.here")).isFalse();
        assertThat(jwtTokenService.isTokenValid("")).isFalse();
    }
}
