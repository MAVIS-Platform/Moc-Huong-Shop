package com.mochuong.identity.service;

import com.mochuong.identity.domain.UserAccount;
import com.mochuong.identity.dto.AuthResponse;
import com.mochuong.identity.dto.LoginRequest;
import com.mochuong.identity.dto.RegisterRequest;
import com.mochuong.identity.dto.UserProfileResponse;
import com.mochuong.identity.exception.DuplicateEmailException;
import com.mochuong.identity.exception.InvalidCredentialsException;
import com.mochuong.identity.exception.UserNotFoundException;
import com.mochuong.identity.repository.UserAccountRepository;
import com.mochuong.identity.security.JwtTokenIssuer;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer jwtTokenIssuer;

    public IdentityService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenIssuer jwtTokenIssuer) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenIssuer = jwtTokenIssuer;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        UserAccount account = UserAccount.createCustomer(normalizedEmail, encodedPassword, request.fullName());
        UserAccount saved = userAccountRepository.save(account);
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return toAuthResponse(account);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return new UserProfileResponse(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                account.getRole(),
                account.getCreatedAt()
        );
    }

    private AuthResponse toAuthResponse(UserAccount account) {
        String token = jwtTokenIssuer.issueToken(account);
        return new AuthResponse(
                token,
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                account.getRole()
        );
    }
}
