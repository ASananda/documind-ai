package com.example.docbot.service;

import com.example.docbot.dto.AuthRequestDto;
import com.example.docbot.dto.AuthResponseDto;
import com.example.docbot.dto.UserProfileDto;
import com.example.docbot.entity.AppUserEntity;
import com.example.docbot.repository.AppUserRepository;
import com.example.docbot.security.AuthUser;
import com.example.docbot.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    public AuthResponseDto signup(AuthRequestDto request) {
        String username = normalizeUsername(request.username());

        if (appUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        AppUserEntity saved = appUserRepository.save(user);

        return new AuthResponseDto(
                jwtService.generateToken(saved.getId(), saved.getUsername()),
                saved.getId(),
                saved.getUsername()
        );
    }

    public AuthResponseDto login(AuthRequestDto request) {
        String username = normalizeUsername(request.username());

        AppUserEntity user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return new AuthResponseDto(
                jwtService.generateToken(user.getId(), user.getUsername()),
                user.getId(),
                user.getUsername()
        );
    }

    public UserProfileDto me() {
        AuthUser authUser = currentUserService.requireUser();
        return new UserProfileDto(authUser.userId(), authUser.username());
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        return username.trim().toLowerCase();
    }
}
