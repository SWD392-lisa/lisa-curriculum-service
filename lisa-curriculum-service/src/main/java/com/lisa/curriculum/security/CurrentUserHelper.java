package com.lisa.curriculum.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUserHelper {
    public static LmsUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LmsUserPrincipal) {
            return (LmsUserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}
