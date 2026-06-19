package com.lisa.curriculum.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;

import java.security.Principal;
import java.util.Collection;

@Getter
@Builder
@AllArgsConstructor
public class LmsUserPrincipal implements Principal {
    private final String userId;
    private final String email;
    private final String displayName;
    private final String roleId;
    private final Collection<? extends GrantedAuthority> authorities;

    @Override
    public String getName() {
        return displayName != null ? displayName : userId;
    }
}
