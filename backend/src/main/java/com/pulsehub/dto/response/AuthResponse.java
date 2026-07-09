package com.pulsehub.dto.response;

public record AuthResponse(
        String token,
        Long id,
        String name,
        String email
) {
}
