package com.mochuong.identity.dto;

import com.mochuong.identity.domain.UserRole;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        UUID userId,
        String email,
        String fullName,
        UserRole role
) {
}
