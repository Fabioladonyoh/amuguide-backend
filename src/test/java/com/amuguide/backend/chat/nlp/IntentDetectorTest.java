package com.amuguide.backend.chat.nlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDetectorTest {

    private final IntentDetector detector = new IntentDetector();

    @Test
    void detectsCoverageQuestions() {
        assertThat(detector.detect("Quelles prestations sont couvertes ?").getIntent())
                .isEqualTo(ChatIntent.COVERAGE);
        assertThat(detector.detect("Est-ce que la consultation est prise en charge ?").getIntent())
                .isEqualTo(ChatIntent.COVERAGE);
        assertThat(detector.detect("Les medicaments sont-ils rembourses ?").getIntent())
                .isEqualTo(ChatIntent.COVERAGE);
    }

    @Test
    void detectsStructureQuestions() {
        IntentDetectionResult nearMe = detector.detect("Y a-t-il une clinique AMU pres de moi ?");

        assertThat(detector.detect("Existe-t-il une pharmacie partenaire a Lome ?").getIntent())
                .isEqualTo(ChatIntent.HOSPITAL_SEARCH);
        assertThat(detector.detect("Trouve-moi un hopital agree a Kara.").getCity())
                .isEqualTo("Kara");
        assertThat(nearMe.getIntent()).isEqualTo(ChatIntent.HOSPITAL_SEARCH);
        assertThat(nearMe.getCity()).isNull();
    }

    @Test
    void detectsMedicationQuestions() {
        assertThat(detector.detect("Le paracetamol est-il dans le referentiel ?").getIntent())
                .isEqualTo(ChatIntent.MEDICATION_SEARCH);
        assertThat(detector.detect("L ibuprofene est-il reconnu ?").getIntent())
                .isEqualTo(ChatIntent.MEDICATION_SEARCH);
    }

    @Test
    void detectsAmuInfoQuestions() {
        assertThat(detector.detect("C est quoi l AMU ?").getIntent())
                .isEqualTo(ChatIntent.AMU_INFO);
        assertThat(detector.detect("Comment fonctionne la prise en charge AMU ?").getIntent())
                .isEqualTo(ChatIntent.AMU_INFO);
    }

    @Test
    void detectsProcedureQuestions() {
        assertThat(detector.detect("Comment utiliser ma carte AMU ?").getIntent())
                .isEqualTo(ChatIntent.PROCEDURE);
        assertThat(detector.detect("Comment renouveler ma carte AMU ?").getIntent())
                .isEqualTo(ChatIntent.PROCEDURE);
    }
}
