package com.amuguide.backend.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationFilesTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    void runtimeProfilesDoNotContainJwtSecretFallbacks() throws IOException {
        String dev = read("src/main/resources/application-dev.properties");
        String prod = read("src/main/resources/application-prod.properties");

        assertThat(dev).contains("jwt.secret=${JWT_SECRET}");
        assertThat(prod).contains("jwt.secret=${JWT_SECRET}");
        assertThat(dev).doesNotContain("jwt.secret=${JWT_SECRET:");
        assertThat(prod).doesNotContain("jwt.secret=${JWT_SECRET:");
    }

    @Test
    void automatedTestsUseDedicatedJwtSecretFallback() throws IOException {
        String test = read("src/test/resources/application.properties");

        assertThat(test).contains("jwt.secret=${JWT_SECRET_TEST:");
        assertThat(test).doesNotContain("jwt.secret=${JWT_SECRET:");
    }

    @Test
    void demoSeedersAreDisabledByDefaultAndInProduction() throws IOException {
        String defaults = read("src/main/resources/application.properties");
        String prod = read("src/main/resources/application-prod.properties");

        assertThat(defaults).contains("amuguide.seed.demo.enabled=false");
        assertThat(defaults).contains("amuguide.seed.reference-prestations.enabled=false");
        assertThat(defaults).contains("amuguide.seed.partner-pharmacies.enabled=false");
        assertThat(defaults).contains("amuguide.seed.legacy-medicaments.enabled=false");
        assertThat(prod).doesNotContain("amuguide.seed.demo.enabled=true");
        assertThat(prod).doesNotContain("amuguide.seed.reference-prestations.enabled=true");
        assertThat(prod).doesNotContain("amuguide.seed.partner-pharmacies.enabled=true");
        assertThat(prod).doesNotContain("amuguide.seed.legacy-medicaments.enabled=true");
    }

    @Test
    void productionDisablesSwaggerByDefault() throws IOException {
        String prod = read("src/main/resources/application-prod.properties");

        assertThat(prod).contains("springdoc.api-docs.enabled=${SPRINGDOC_API_DOCS_ENABLED:false}");
        assertThat(prod).contains("springdoc.swagger-ui.enabled=${SPRINGDOC_SWAGGER_UI_ENABLED:false}");
    }

    @Test
    void dataInitializerDoesNotLogSeedPasswordsOrIdentifiers() throws IOException {
        String initializer = read("src/main/java/com/amuguide/backend/config/DataInitializer.java");

        assertThat(initializer).doesNotContain("(mdp:");
        assertThat(initializer).doesNotContain("System.out.printf(\"  %-14s");
        assertThat(initializer).doesNotContain("System.out.printf(\"  %-7s");
        assertThat(initializer).doesNotContain("assure.getNumeroAMU())");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
