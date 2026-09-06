package com.subdual.auth_service.service;

import com.subdual.auth_service.dto.AuthResponse;
import com.subdual.auth_service.dto.LoginRequest;
import com.subdual.auth_service.dto.RegisterRequest;
import com.subdual.auth_service.dto.UserDto;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserDto getCurrentUser(String userId);
}
