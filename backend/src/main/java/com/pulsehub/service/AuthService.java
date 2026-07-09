package com.pulsehub.service;

import com.pulsehub.dto.request.LoginRequest;
import com.pulsehub.dto.request.RegisterRequest;
import com.pulsehub.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
