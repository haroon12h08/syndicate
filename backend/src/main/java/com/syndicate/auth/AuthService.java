package com.syndicate.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.syndicate.auth.dto.AuthResponse;
import com.syndicate.auth.dto.LoginRequest;
import com.syndicate.auth.dto.MfaLoginRequest;
import com.syndicate.auth.dto.MfaSetupResponse;
import com.syndicate.auth.dto.RegisterRequest;
import com.syndicate.auth.totp.TotpService;
import com.syndicate.common.BadRequestException;
import com.syndicate.organization.Organization;
import com.syndicate.organization.OrganizationService;
import com.syndicate.organization.dto.OrganizationDto;
import com.syndicate.user.User;
import com.syndicate.user.UserDto;
import com.syndicate.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final TotpService totpService;
    private final com.syndicate.metrics.SyndicateMetrics syndicateMetrics;

    public AuthService(UserRepository userRepository,
                        OrganizationService organizationService,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        SessionService sessionService,
                        TotpService totpService,
                        com.syndicate.metrics.SyndicateMetrics syndicateMetrics) {
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.totpService = totpService;
        this.syndicateMetrics = syndicateMetrics;
    }

    /** Exchanges a valid session cookie for a fresh access token, rotating the cookie. */
    @Transactional
    public AuthResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = sessionService.refreshSession(httpRequest, httpResponse);
        return AuthResponse.withoutOrganization(jwtService.generateToken(user.getId(), user.getEmail()),
                UserDto.from(user));
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        sessionService.endSession(httpRequest, httpResponse);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = userRepository.save(new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName()
        ));
        Organization organization = organizationService.createOrganizationWithOwner(
                request.organizationName(), request.organizationType(), user);

        sessionService.startSession(user, httpRequest, httpResponse);
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user), OrganizationDto.from(organization));
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.isLocked()) {
            syndicateMetrics.incrementLockouts();
            throw new BadRequestException("Account is temporarily locked due to consecutive failed login attempts. Please try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepository.save(user);
            syndicateMetrics.incrementLoginFailures();
            if (user.isLocked()) {
                syndicateMetrics.incrementLockouts();
            }
            throw new BadCredentialsException("Invalid email or password");
        }

        user.resetFailedLogins();
        userRepository.save(user);

        if (user.isMfaEnabled()) {
            String challengeToken = jwtService.generateMfaChallengeToken(user.getId(), user.getEmail());
            return AuthResponse.mfaRequired(challengeToken);
        }

        sessionService.startSession(user, httpRequest, httpResponse);
        return AuthResponse.withoutOrganization(jwtService.generateToken(user.getId(), user.getEmail()),
                UserDto.from(user));
    }

    @Transactional
    public MfaSetupResponse setupMfa(User user) {
        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        userRepository.save(user);
        String qrUri = totpService.getQrCodeUri(user.getEmail(), secret, "Syndicate");
        return new MfaSetupResponse(secret, qrUri);
    }

    @Transactional
    public UserDto enableMfa(User user, int code) {
        if (user.getMfaSecret() == null) {
            throw new BadRequestException("MFA setup has not been initiated");
        }
        if (!totpService.verifyCode(user.getMfaSecret(), code)) {
            throw new BadRequestException("Invalid MFA verification code");
        }
        user.setMfaEnabled(true);
        User saved = userRepository.save(user);
        return UserDto.from(saved);
    }

    @Transactional
    public UserDto disableMfa(User user, int code) {
        if (!user.isMfaEnabled()) {
            throw new BadRequestException("MFA is not enabled");
        }
        if (!totpService.verifyCode(user.getMfaSecret(), code)) {
            throw new BadRequestException("Invalid MFA verification code");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        User saved = userRepository.save(user);
        return UserDto.from(saved);
    }

    @Transactional
    public AuthResponse verifyMfaLogin(MfaLoginRequest request, HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        UUID userId = jwtService.extractMfaUserId(request.mfaToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!totpService.verifyCode(user.getMfaSecret(), request.code())) {
            throw new BadCredentialsException("Invalid MFA verification code");
        }

        sessionService.startSession(user, httpRequest, httpResponse);
        return AuthResponse.withoutOrganization(jwtService.generateToken(user.getId(), user.getEmail()),
                UserDto.from(user));
    }
}
