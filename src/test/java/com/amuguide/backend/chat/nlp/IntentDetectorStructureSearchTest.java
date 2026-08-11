package com.amuguide.backend.chat.nlp;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDetectorStructureSearchTest {

    private final IntentDetector intentDetector = new IntentDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "Quelles sont les structures conventionnées ?",
            "Quels hôpitaux sont conventionnés ?",
            "Quelles pharmacies sont conventionnées ?",
            "Je cherche un hôpital conventionné.",
            "Je cherche une pharmacie conventionnée.",
            "Quelles structures sont disponibles à Lomé ?",
            "Quels hôpitaux sont disponibles dans la région Maritime ?",
            "Quelles pharmacies sont conventionnées dans les Plateaux ?",
            "Où puis-je trouver une pharmacie AMU ?",
            "Je veux un hôpital partenaire.",
            "Montre-moi les centres conventionnés.",
            "Y a-t-il une pharmacie conventionnée à Lomé ?"
    })
    void detectsStructureSearchNaturalQuestions(String question) {
        IntentDetectionResult result = intentDetector.detect(question);

        assertThat(result.getIntent()).isEqualTo(ChatIntent.HOSPITAL_SEARCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ou se trouve la pharmacie Bon Pasteur ?",
            "Quel est le numero de la pharmacie El-Nissi ?"
    })
    void doesNotExtractPharmacyNameAsCity(String question) {
        IntentDetectionResult result = intentDetector.detect(question);

        assertThat(result.getIntent()).isEqualTo(ChatIntent.HOSPITAL_SEARCH);
        assertThat(result.getCity()).isNull();
    }
}
