package com.amuguide.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void usesExplicitAllowedOriginsInsteadOfWildcard() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(
                corsConfig,
                "allowedOrigins",
                "http://localhost:4200, https://amu-guide.example"
        );
        ReflectionTestUtils.setField(corsConfig, "allowedOriginPatterns", "");

        CorsConfiguration configuration = corsConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/chat/status"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("http://localhost:4200", "https://amu-guide.example");
        assertThat(configuration.getAllowedOriginPatterns()).isNullOrEmpty();
    }

    @Test
    void usesAllowedOriginPatternsWhenConfigured() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", "http://localhost:4200");
        ReflectionTestUtils.setField(
                corsConfig,
                "allowedOriginPatterns",
                "http://localhost:*, http://127.0.0.1:*"
        );

        CorsConfiguration configuration = corsConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/chat/status"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isNullOrEmpty();
        assertThat(configuration.getAllowedOriginPatterns())
                .containsExactly("http://localhost:*", "http://127.0.0.1:*");
    }
}
