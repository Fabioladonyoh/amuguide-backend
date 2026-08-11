package com.amuguide.backend.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdministrateurSerializationTest {

    @Test
    void doesNotSerializePasswordHash() throws Exception {
        Administrateur administrateur = Administrateur.builder()
                .idAdmin(1L)
                .nom("Admin")
                .prenom("AMU")
                .email("admin@example.test")
                .login("admin")
                .motDePasse("$2a$10$secretHash")
                .actif(true)
                .build();

        String json = new ObjectMapper().writeValueAsString(administrateur);

        assertThat(json).doesNotContain("motDePasse");
        assertThat(json).doesNotContain("secretHash");
    }
}
