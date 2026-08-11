package com.amuguide.backend.config;

import com.amuguide.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // ── Swagger (accès libre en développement) ─────────────────
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // ── Authentification ────────────────────────────────────────
                .requestMatchers("/api/auth/**").permitAll()

                // ── Lecture publique des référentiels ───────────────────────
                .requestMatchers(HttpMethod.GET, "/api/prestations/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/structures/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/verifications/**").permitAll()

                // ── Chatbot public ──────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/chatbot/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/chat/status").permitAll()

                // ── Formulaire de contact public ────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()

                // ── Admin : gestion des référentiels ────────────────────────
                .requestMatchers(HttpMethod.POST,   "/api/prestations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/prestations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/prestations/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/structures/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/structures/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/structures/**").hasRole("ADMIN")

                // ── Admin : demandes, assurés, contact, stats ───────────────
                .requestMatchers("/api/demandes/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // ── Espace assuré authentifié ───────────────────────────────
                .requestMatchers("/api/me/**").hasRole("ASSURE")
                .requestMatchers(HttpMethod.GET, "/api/assure/pharmacies/**").hasRole("ASSURE")
                .requestMatchers("/api/assure/**").hasRole("ASSURE")

                // ── Tout le reste requiert une authentification ─────────────
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(401);
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Non authentifié\"," +
                        "\"message\":\"Token JWT manquant ou invalide\"}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(403);
                    response.getWriter().write(
                        "{\"status\":403,\"error\":\"Accès refusé\"," +
                        "\"message\":\"Vous n'avez pas les droits nécessaires pour cette ressource\"}"
                    );
                })
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Spring Security 7 : UserDetailsService est requis dans le constructeur
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
