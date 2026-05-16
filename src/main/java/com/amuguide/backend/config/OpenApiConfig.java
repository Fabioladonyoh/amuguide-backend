package com.amuguide.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "AMU-Guide API",
        version = "1.0",
        description = "API backend — Assurance Maladie Universelle. " +
                      "Cliquez sur 'Authorize' et collez votre token JWT (sans le préfixe 'Bearer')."
    ),
    security = { @SecurityRequirement(name = "bearerAuth") }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Token obtenu via POST /api/auth/assure/login ou /api/auth/admin/login"
)
public class OpenApiConfig {
}
