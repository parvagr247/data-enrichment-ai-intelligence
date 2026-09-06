package com.subdual.auth_service.dto;

import java.time.Instant;

public record UserDto(
        String id,
        String name,
        String email,
        Instant createdAt
) {}
