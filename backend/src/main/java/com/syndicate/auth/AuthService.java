package com.syndicate.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.syndicate.auth.dto.AuthResponse;
import com.syndicate.auth.dto.LoginRequest;
import com.syndicate.auth.dto.RegisterRequest;
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

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;

    public AuthService(UserRepository userRepository,
                        OrganizationService organizationService,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        SessionService sessionService) {
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
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

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        sessionService.startSession(user, httpRequest, httpResponse);
        return AuthResponse.withoutOrganization(jwtService.generateToken(user.getId(), user.getEmail()),
                UserDto.from(user));
    }
}
