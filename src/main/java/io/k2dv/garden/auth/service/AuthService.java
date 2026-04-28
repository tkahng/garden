package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.dto.*;
import io.k2dv.garden.auth.model.Identity;
import io.k2dv.garden.auth.model.IdentityProvider;
import io.k2dv.garden.auth.model.TokenType;
import io.k2dv.garden.auth.repository.IdentityRepository;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.iam.service.IamService;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final IdentityRepository identityRepo;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final IamService iamService;
    private final EmailService emailService;
    private final AppProperties props;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public AuthTokenResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }

        User user = new User();
        user.setEmail(req.email());
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setStatus(UserStatus.UNVERIFIED);
        user = userRepo.save(user);

        Identity identity = new Identity();
        identity.setUserId(user.getId());
        identity.setProvider(IdentityProvider.CREDENTIALS);
        identity.setAccountId(user.getId().toString());
        identity.setPasswordHash(passwordEncoder.encode(req.password()));
        identityRepo.save(identity);

        iamService.assignRoleByName(user.getId(), "CUSTOMER");

        String verificationToken = tokenService.createToken(
            user.getId(), TokenType.EMAIL_VERIFICATION,
            props.getJwt().getEmailVerificationTtl());
        emailService.sendEmailVerification(user.getEmail(), verificationToken);

        return mintTokenPair(user);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.email())
            .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password"));

        Identity identity = identityRepo.findByUserIdAndProvider(user.getId(), IdentityProvider.CREDENTIALS)
            .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), identity.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Your account has been suspended");
        }

        return mintTokenPair(user);
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshRequest req) {
        UUID userId = tokenService.validateAndConsume(req.refreshToken(), TokenType.REFRESH_TOKEN);
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Your account has been suspended");
        }
        return mintTokenPair(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        try {
            tokenService.validateAndConsume(rawRefreshToken, TokenType.REFRESH_TOKEN);
        } catch (UnauthorizedException ignored) {
            // Idempotent — already consumed or expired
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID userId = tokenService.validateAndConsume(rawToken, TokenType.EMAIL_VERIFICATION);
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.setEmailVerifiedAt(Instant.now());
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);
    }

    @Transactional
    public void resendVerification(String email) {
        userRepo.findByEmail(email).ifPresent(user -> {
            if (user.getEmailVerifiedAt() != null) return;
            String token = tokenService.createToken(
                user.getId(), TokenType.EMAIL_VERIFICATION,
                props.getJwt().getEmailVerificationTtl());
            emailService.sendEmailVerification(user.getEmail(), token);
        });
        // Silent if email not found — prevents account enumeration
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepo.findByEmail(email).ifPresent(user -> {
            String token = tokenService.createToken(
                user.getId(), TokenType.PASSWORD_RESET,
                props.getJwt().getPasswordResetTtl());
            emailService.sendPasswordReset(user.getEmail(), token);
        });
        // Silent if email not found — prevents user enumeration
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, PasswordResetConfirmRequest req) {
        UUID userId = tokenService.validateAndConsume(rawToken, TokenType.PASSWORD_RESET);
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        Identity identity = identityRepo.findByUserIdAndProvider(user.getId(), IdentityProvider.CREDENTIALS)
            .orElseThrow(() -> new NotFoundException("IDENTITY_NOT_FOUND", "No password credentials for this account"));
        identity.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        identityRepo.save(identity);
    }

    @Transactional
    public void updatePassword(UUID userId, UpdatePasswordRequest req) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        Identity identity = identityRepo.findByUserIdAndProvider(user.getId(), IdentityProvider.CREDENTIALS)
            .orElseThrow(() -> new NotFoundException("IDENTITY_NOT_FOUND", "No password credentials for this account"));
        if (!passwordEncoder.matches(req.currentPassword(), identity.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Current password is incorrect");
        }
        identity.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        identityRepo.save(identity);
    }

    private AuthTokenResponse mintTokenPair(User user) {
        List<String> permissions = iamService.loadPermissionsForUser(user.getId());
        String accessToken = jwtService.mintAccessToken(user, permissions);
        String refreshToken = tokenService.createToken(
            user.getId(), TokenType.REFRESH_TOKEN, props.getJwt().getRefreshTokenTtl());
        return new AuthTokenResponse(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepo.existsByEmail(email);
    }
}
