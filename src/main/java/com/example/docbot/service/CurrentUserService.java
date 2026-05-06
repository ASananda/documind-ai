package com.example.docbot.service;

import com.example.docbot.security.AuthUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public AuthUser requireUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof AuthUser authUser) {
            return authUser;
        }

        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthUser authUser) {
            return authUser;
        }

        throw new IllegalStateException("Authenticated user not found");
    }
}
