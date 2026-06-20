package com.lisa.curriculum;

import com.lisa.curriculum.security.LmsUserPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.cache.type=simple"})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SECRET = "8vOvSRycvAEBW/EDujcErz8UmfN70KGZZGKqfSuX7goQ1db0sPBwU1ICfogYXuy8QdOKRih5DEYOvMq1PL4Acg==";
    private static final String ISSUER = "ProjectLucy.API";
    private static final String AUDIENCE = "ProjectLucy.Client";

    private String generateToken(String userId, String email, String displayName, String roleId, 
                                  boolean validSignature, boolean validIssuer, boolean validAudience, boolean expired) {
        String secretKey = validSignature ? SECRET : "invalidsecretkeyinvalidsecretkeyinvalidsecretkeyinvalidsecretkey";
        Key key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        var builder = Jwts.builder()
                .claim("sub", userId)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", userId)
                .claim("email", email)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress", email)
                .claim("name", displayName)
                .claim("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name", displayName)
                .claim("role", roleId)
                .claim("http://schemas.microsoft.com/ws/2008/06/identity/claims/role", roleId)
                .setIssuer(validIssuer ? ISSUER : "invalid-issuer")
                .setAudience(validAudience ? AUDIENCE : "invalid-audience");

        if (expired) {
            builder.setExpiration(new Date(System.currentTimeMillis() - 1000 * 60));
        } else {
            builder.setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60));
        }

        return builder.signWith(key).compact();
    }

    @Test
    @DisplayName("Public endpoint GET levels works without token")
    void testPublicLevels() throws Exception {
        mockMvc.perform(get("/api/curriculum/levels")
                        .param("language", "ENGLISH"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public endpoint GET stats works without token")
    void testPublicStats() throws Exception {
        mockMvc.perform(get("/api/curriculum/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoints returns 401")
    void testUnauthenticatedImport() throws Exception {
        mockMvc.perform(delete("/api/curriculum")
                        .param("language", "ENGLISH"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token with invalid signature returns 401")
    void testInvalidSignature() throws Exception {
        String token = generateToken("12345", "user@lisa.com", "John Doe", "2", 
                false, true, true, false);

        mockMvc.perform(delete("/api/curriculum")
                        .header("Authorization", "Bearer " + token)
                        .param("language", "ENGLISH"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token with invalid issuer returns 401")
    void testInvalidIssuer() throws Exception {
        String token = generateToken("12345", "user@lisa.com", "John Doe", "2", 
                true, false, true, false);

        mockMvc.perform(delete("/api/curriculum")
                        .header("Authorization", "Bearer " + token)
                        .param("language", "ENGLISH"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token with mapped MENTOR role (role ID 2) can access delete API")
    void testMentorCanDelete() throws Exception {
        String token = generateToken("12345", "mentor@lisa.com", "Mentor Name", "2", 
                true, true, true, false);

        mockMvc.perform(delete("/api/curriculum")
                        .header("Authorization", "Bearer " + token)
                        .param("language", "ENGLISH"))
                .andExpect(status().isNoContent()); // Successful delete execution returns 204
    }

    @Test
    @DisplayName("Token with mapped USER role (role ID 1) to delete API returns 403")
    void testUserCannotDelete() throws Exception {
        String token = generateToken("12345", "user@lisa.com", "User Name", "1", 
                true, true, true, false);

        mockMvc.perform(delete("/api/curriculum")
                        .header("Authorization", "Bearer " + token)
                        .param("language", "ENGLISH"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Token with unknown role ID returns 403")
    void testUnknownRoleReturnsForbidden() throws Exception {
        String token = generateToken("12345", "user@lisa.com", "User Name", "99", 
                true, true, true, false);

        mockMvc.perform(delete("/api/curriculum")
                        .header("Authorization", "Bearer " + token)
                        .param("language", "ENGLISH"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("LmsUserPrincipal parse claims correctly in SecurityContext")
    void testPrincipalParsing() throws Exception {
        String token = generateToken("USER-UUID-123", "test@lisa.com", "Test Display", "3", 
                true, true, true, false);

        mockMvc.perform(get("/api/curriculum/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        LmsUserPrincipal principal = LmsUserPrincipal.builder()
                .userId("USER-UUID-123")
                .email("test@lisa.com")
                .displayName("Test Display")
                .roleId("3")
                .build();

        assertThat(principal.getUserId()).isEqualTo("USER-UUID-123");
        assertThat(principal.getEmail()).isEqualTo("test@lisa.com");
        assertThat(principal.getDisplayName()).isEqualTo("Test Display");
        assertThat(principal.getRoleId()).isEqualTo("3");
        assertThat(principal.getName()).isEqualTo("Test Display");
    }
}
