package com.subdual.auth_service.dto;

public record AuthResponse(
        String token,
        UserDto user
) {}
