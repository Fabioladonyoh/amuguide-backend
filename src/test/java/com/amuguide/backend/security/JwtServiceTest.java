package com.amuguide.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "aW11Z3VpZGViYWNrZW5kand0c2VjcmV0a2V5MjAyNHZlcnlzdHJvbmc=";

    @Test
    void validatesTokenForSameSubject() {
        JwtService service = jwtServiceWithExpiration(60_000);
        String token = service.generateToken("ASSURE:AMU001", Map.of("role", "ASSURE"));
        User user = (User) User.withUsername("ASSURE:AMU001")
                .password("unused")
                .roles("ASSURE")
                .build();

        assertThat(service.extractUsername(token)).isEqualTo("ASSURE:AMU001");
        assertThat(service.isTokenValid(token, user)).isTrue();
    }

    @Test
    void rejectsTokenForDifferentSubject() {
        JwtService service = jwtServiceWithExpiration(60_000);
        String token = service.generateToken("ASSURE:AMU001", Map.of());
        User user = (User) User.withUsername("ASSURE:AMU002")
                .password("unused")
                .roles("ASSURE")
                .build();

        assertThat(service.isTokenValid(token, user)).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = jwtServiceWithExpiration(-1_000);
        String token = service.generateToken("ASSURE:AMU001", Map.of());
        User user = (User) User.withUsername("ASSURE:AMU001")
                .password("unused")
                .roles("ASSURE")
                .build();

        assertThatThrownBy(() -> service.isTokenValid(token, user))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsMalformedToken() {
        JwtService service = jwtServiceWithExpiration(60_000);

        assertThatThrownBy(() -> service.extractUsername("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    private JwtService jwtServiceWithExpiration(long expiration) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(service, "expiration", expiration);
        return service;
    }
}
