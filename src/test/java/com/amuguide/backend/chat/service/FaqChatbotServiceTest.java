package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.FaqChatbot;
import com.amuguide.backend.enums.FaqChatbotCategorie;
import com.amuguide.backend.repository.FaqChatbotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqChatbotServiceTest {

    @Mock
    private FaqChatbotRepository repository;

    @Mock
    private ResponseGenerator responseGenerator;

    private FaqChatbotService service;

    @BeforeEach
    void setUp() {
        service = new FaqChatbotService(repository, responseGenerator);
        lenient().when(repository.findByActifTrue()).thenReturn(faqSeeds());
        lenient().when(responseGenerator.generate(any())).thenAnswer(invocation -> {
            IntentDetectionResult detection = invocation.getArgument(0);
            return ChatbotResponseDTO.builder()
                    .answer("Reponse dynamique " + detection.getIntent())
                    .message("Reponse dynamique " + detection.getIntent())
                    .intent(detection.getIntent().name())
                    .found(true)
                    .build();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quelles prestations sont couvertes ?",
            "Est-ce que la consultation est prise en charge ?",
            "Quel est le taux de couverture pour l'hospitalisation ?",
            "Les medicaments sont-ils rembourses ?",
            "La radiologie est-elle couverte ?",
            "Quels documents faut-il pour une consultation ?",
            "Existe-t-il une pharmacie partenaire a Lome ?",
            "Trouve-moi un hopital agree a Kara.",
            "Y a-t-il une clinique AMU pres de moi ?",
            "Quelles structures de sante sont agreees ?",
            "Ou trouver une pharmacie agreee ?",
            "Le paracetamol est-il dans le referentiel ?",
            "Est-ce que l'amoxicilline est prise en charge ?",
            "Quels medicaments sont disponibles dans le referentiel AMU ?",
            "L'ibuprofene est-il reconnu ?",
            "C'est quoi l'AMU ?",
            "A quoi sert l'assurance maladie universelle ?",
            "Comment fonctionne la prise en charge AMU ?",
            "Quels documents dois-je fournir ?",
            "Comment utiliser ma carte AMU ?",
            "Que faut-il pour beneficier d'une prestation ?",
            "Comment renouveler ma carte AMU ?"
    })
    void shouldAnswerSeededQuestions(String question) {
        Optional<ChatbotResponseDTO> response = service.answer(detection(question, ChatIntent.FALLBACK));

        assertThat(response).isPresent();
        assertThat(response.get().getAnswer()).isNotBlank();
        assertThat(response.get().getFound()).isTrue();
        assertThat(response.get().getSources()).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "La consultation est-elle couverte ?",
            "Je cherche une pharmacie a Lome.",
            "Puis-je utiliser ma carte AMU ?",
            "Comment refaire ma carte ?"
    })
    void shouldAnswerQuestionVariants(String question) {
        Optional<ChatbotResponseDTO> response = service.answer(detection(question, ChatIntent.FALLBACK));

        assertThat(response).isPresent();
        assertThat(response.get().getAnswer()).isNotBlank();
    }

    @Test
    void shouldNormalizeAccentsCaseAndPunctuation() {
        assertThat(service.normalize("C'est quoi l'AMU ?")).isEqualTo("c est quoi l amu");
        assertThat(service.normalize("À Lomé, une PHARMACIE agréée !")).isEqualTo("a lome une pharmacie agreee");
    }

    private IntentDetectionResult detection(String question, ChatIntent intent) {
        return IntentDetectionResult.builder()
                .intent(intent)
                .normalizedMessage(service.normalize(question))
                .score(0.8)
                .build();
    }

    private List<FaqChatbot> faqSeeds() {
        return List.of(
                faq("PRESTATIONS_LISTE", "Quelles prestations sont couvertes ?", FaqChatbotCategorie.PRESTATION, "prestations couvertes, prestations prises en charge, actes couverts, couverture amu, liste prestations"),
                faq("PRESTATION_CONSULTATION", "Est-ce que la consultation est prise en charge ?", FaqChatbotCategorie.PRESTATION, "consultation, consulter, medecin, prise en charge consultation, consultation couverte, remboursement consultation"),
                faq("PRESTATION_HOSPITALISATION", "Quel est le taux de couverture pour l'hospitalisation ?", FaqChatbotCategorie.PRESTATION, "hospitalisation, hospitalise, hopital, taux hospitalisation, couverture hospitalisation"),
                faq("PRESTATION_MEDICAMENT", "Les medicaments sont-ils rembourses ?", FaqChatbotCategorie.PRESTATION, "medicament, medicaments, remboursement medicament, prise en charge medicament, ordonnance"),
                faq("PRESTATION_RADIOLOGIE", "La radiologie est-elle couverte ?", FaqChatbotCategorie.PRESTATION, "radiologie, radio, imagerie, radiographie, echographie"),
                faq("PRESTATION_CONSULTATION_DOCUMENTS", "Quels documents faut-il pour une consultation ?", FaqChatbotCategorie.PRESTATION, "documents consultation, piece consultation, carte amu consultation"),
                faq("STRUCTURE_PHARMACIE_LOME", "Existe-t-il une pharmacie partenaire a Lome ?", FaqChatbotCategorie.STRUCTURE_SANTE, "pharmacie, partenaire, agreee, lome, officine, proche"),
                faq("STRUCTURE_HOPITAL_KARA", "Trouve-moi un hopital agree a Kara.", FaqChatbotCategorie.STRUCTURE_SANTE, "hopital, chu, agree, kara, structure kara"),
                faq("STRUCTURE_CLINIQUE_PROCHE", "Y a-t-il une clinique AMU pres de moi ?", FaqChatbotCategorie.STRUCTURE_SANTE, "clinique, amu, pres de moi, proche, autour de moi"),
                faq("STRUCTURES_AGREEES", "Quelles structures de sante sont agreees ?", FaqChatbotCategorie.STRUCTURE_SANTE, "structures agreees, structures de sante, centres agrees"),
                faq("STRUCTURE_PHARMACIE_AGREEE", "Ou trouver une pharmacie agreee ?", FaqChatbotCategorie.STRUCTURE_SANTE, "pharmacie agreee, trouver pharmacie, officine agreee"),
                faq("MEDICAMENT_PARACETAMOL", "Le paracetamol est-il dans le referentiel ?", FaqChatbotCategorie.MEDICAMENT, "paracetamol, doliprane, panadol, dafalgan"),
                faq("MEDICAMENT_AMOXICILLINE", "Est-ce que l'amoxicilline est prise en charge ?", FaqChatbotCategorie.MEDICAMENT, "amoxicilline, antibiotique, referentiel amoxicilline"),
                faq("MEDICAMENTS_REFERENTIEL", "Quels medicaments sont disponibles dans le referentiel AMU ?", FaqChatbotCategorie.MEDICAMENT, "liste medicaments, referentiel amu, medicaments disponibles"),
                faq("MEDICAMENT_IBUPROFENE", "L'ibuprofene est-il reconnu ?", FaqChatbotCategorie.MEDICAMENT, "ibuprofene, anti inflammatoire, referentiel ibuprofene"),
                faq("AMU_DEFINITION", "C'est quoi l'AMU ?", FaqChatbotCategorie.INFORMATION_AMU, "amu, assurance maladie universelle, definition amu, c'est quoi amu"),
                faq("AMU_OBJECTIF", "A quoi sert l'assurance maladie universelle ?", FaqChatbotCategorie.INFORMATION_AMU, "a quoi sert amu, objectif amu, role assurance maladie"),
                faq("AMU_FONCTIONNEMENT", "Comment fonctionne la prise en charge AMU ?", FaqChatbotCategorie.INFORMATION_AMU, "fonctionnement amu, prise en charge amu, taux, conditions"),
                faq("PROCEDURE_DOCUMENTS", "Quels documents dois-je fournir ?", FaqChatbotCategorie.PROCEDURE, "documents, pieces, fournir, justificatifs, dossier"),
                faq("PROCEDURE_CARTE_UTILISATION", "Comment utiliser ma carte AMU ?", FaqChatbotCategorie.PROCEDURE, "carte amu, utiliser carte, presenter carte, carte valide"),
                faq("PROCEDURE_PRESTATION", "Que faut-il pour beneficier d'une prestation ?", FaqChatbotCategorie.PROCEDURE, "beneficier prestation, que faut il, condition prestation"),
                faq("PROCEDURE_CARTE_RENOUVELLEMENT", "Comment renouveler ma carte AMU ?", FaqChatbotCategorie.PROCEDURE, "renouveler carte, renouvellement carte, carte expiree, refaire carte, perte carte")
        );
    }

    private FaqChatbot faq(String code, String question, FaqChatbotCategorie categorie, String motsCles) {
        return FaqChatbot.builder()
                .id((long) code.hashCode())
                .code(code)
                .question(question)
                .reponse("Reponse FAQ " + code)
                .categorie(categorie)
                .motsCles(motsCles)
                .actif(true)
                .priorite(10)
                .build();
    }
}
