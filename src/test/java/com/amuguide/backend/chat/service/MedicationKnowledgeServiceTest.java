package com.amuguide.backend.chat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationKnowledgeServiceTest {

    private MedicationKnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new MedicationKnowledgeService();
        ReflectionTestUtils.invokeMethod(service, "load");
    }

    @Test
    void detectsMedicationListRequest() {
        assertThat(service.isListRequest("donne moi la liste des medicaments")).isTrue();
        assertThat(service.list(10)).isNotEmpty();
    }

    @Test
    void resolvesPanadolToParacetamolAndFindsReferentialEntries() {
        List<String> matches = service.search("le PANADOL est pris en charge par l'INAM ?", 5);

        assertThat(service.resolveBrand("PANADOL")).contains("PARACETAMOL");
        assertThat(matches).anyMatch(match -> match.toLowerCase().contains("paracetamol"));
    }
}
