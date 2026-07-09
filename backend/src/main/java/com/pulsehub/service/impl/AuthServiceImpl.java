package com.pulsehub.service.impl;

import com.pulsehub.dto.request.LoginRequest;
import com.pulsehub.dto.request.RegisterRequest;
import com.pulsehub.dto.response.AuthResponse;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.exception.DuplicateResourceException;
import com.pulsehub.exception.InvalidCredentialsException;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.security.JwtService;
import com.pulsehub.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .status(UserStatus.OFFLINE)
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(toUserDetails(saved));

        return new AuthResponse(token, saved.getId(), saved.getName(), saved.getEmail());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(toUserDetails(user));
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail());
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities("ROLE_USER")
                .build();
    }

}
