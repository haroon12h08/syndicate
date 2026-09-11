package com.syndicate.auth;

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

    public AuthService(UserRepository userRepository,
                        OrganizationService organizationService,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user), OrganizationDto.from(organization));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return AuthResponse.withoutOrganization(token, UserDto.from(user));
    }
}
