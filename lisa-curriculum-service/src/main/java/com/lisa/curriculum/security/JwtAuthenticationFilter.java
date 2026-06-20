package com.lisa.curriculum.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        try {
            byte[] keyBytes = jwtProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
            Key key = Keys.hmacShaKeyFor(keyBytes);

            JwtParser parser = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(jwtProperties.getJwt().getIssuer())
                    .requireAudience(jwtProperties.getJwt().getAudience())
                    .build();

            Claims claims = parser.parseClaimsJws(token).getBody();

            String userId = getClaim(claims, "sub", "nameid", "nameidentifier", 
                    "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier");
            String email = getClaim(claims, "email", 
                    "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
            String displayName = getClaim(claims, "name", "unique_name", 
                    "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");
            String roleId = getClaim(claims, "role", 
                    "http://schemas.microsoft.com/ws/2008/06/identity/claims/role");

            if (userId != null) {
                String mappedRole = null;
                if (roleId != null) {
                    mappedRole = jwtProperties.getRoleIdMap().get(roleId);
                }
                if (mappedRole == null) {
                    log.warn("Unknown role: {}, rejecting request with 403", roleId);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Unknown role ID: " + roleId + "\"}");
                    return;
                }

                List<SimpleGrantedAuthority> authorities = buildAuthorities(mappedRole);

                LmsUserPrincipal principal = LmsUserPrincipal.builder()
                        .userId(userId)
                        .email(email)
                        .displayName(displayName)
                        .roleId(roleId)
                        .authorities(authorities)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Successfully authenticated user: {} with role: {}", userId, mappedRole);
            }
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"JWT validation failed: " + e.getMessage() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> buildAuthorities(String mappedRole) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(mappedRole));
        if ("ROLE_MENTOR".equals(mappedRole)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PRO"));
        }
        if ("ROLE_CREATOR".equals(mappedRole)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER"));
        }
        return authorities;
    }

    private String getClaim(Claims claims, String... keys) {
        for (String key : keys) {
            Object val = claims.get(key);
            if (val instanceof String) {
                return ((String) val).trim();
            } else if (val != null) {
                return val.toString().trim();
            }
        }
        return null;
    }
}
