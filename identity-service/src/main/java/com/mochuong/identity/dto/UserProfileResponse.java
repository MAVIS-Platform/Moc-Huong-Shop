package com.mochuong.identity.dto;

import com.mochuong.identity.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        Instant createdAt
) {
}
