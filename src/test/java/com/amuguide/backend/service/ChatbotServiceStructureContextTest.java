package com.amuguide.backend.service;

import com.amuguide.backend.chat.nlp.IntentDetector;
import com.amuguide.backend.chat.service.AiAgentService;
import com.amuguide.backend.chat.service.FaqChatbotService;
import com.amuguide.backend.chat.service.HospitalisationTarifChatbotService;
import com.amuguide.backend.chat.service.MedicamentChatbotSearchService;
import com.amuguide.backend.chat.service.MedicationSessionContextService;
import com.amuguide.backend.chat.service.PrestationSessionContextService;
import com.amuguide.backend.chat.service.ResponseGenerator;
import com.amuguide.backend.chat.service.StructureSessionContextService;
import com.amuguide.backend.dto.ChatbotRequestDTO;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.entity.Pharmacie;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.ChatHistoryRepository;
import com.amuguide.backend.repository.MedicamentRepository;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceStructureContextTest {

    @Mock
    private PrestationRepository prestationRepository;

    @Mock
    private StructureSanteRepository structureSanteRepository;

    @Mock
    private PharmacieRepository pharmacieRepository;

    @Mock
    private com.amuguide.backend.chat.service.MedicationKnowledgeService medicationKnowledgeService;

    @Mock
    private AiAgentService aiAgentService;

    @Mock
    private MedicamentRepository medicamentRepository;

    @Mock
    private FaqChatbotService faqChatbotService;

    @Mock
    private ChatHistoryRepository chatHistoryRepository;

    @Mock
    private AssureAMURepository assureAMURepository;

    @Mock
    private HospitalisationTarifChatbotService hospitalisationTarifChatbotService;

    private ChatbotService chatbotService;
    private StructureSante hopitalA;
    private StructureSante cliniqueB;
    private StructureSante centreSansAdresse;
    private StructureSante pharmacieCentraleOfficielle;
    private StructureSante pharmacieBonPasteurOfficielle;
    private StructureSante pharmacieElNissiOfficielle;
    private Pharmacie pharmacieCentrale;
    private Pharmacie pharmacieAgoe;
    private Pharmacie pharmacieSansTelephone;
    private Prestation consultation;
    private Medicament doliprane;

    @BeforeEach
    void setUp() {
        MedicamentChatbotSearchService medicamentChatbotSearchService = new MedicamentChatbotSearchService(medicamentRepository);
        ResponseGenerator responseGenerator = new ResponseGenerator(
                prestationRepository,
                structureSanteRepository,
                pharmacieRepository,
                medicationKnowledgeService,
                medicamentChatbotSearchService,
                hospitalisationTarifChatbotService,
                aiAgentService
        );
        chatbotService = new ChatbotService(
                new IntentDetector(),
                faqChatbotService,
                responseGenerator,
                chatHistoryRepository,
                assureAMURepository,
                new MedicationSessionContextService(),
                medicamentChatbotSearchService,
                new PrestationSessionContextService(),
                new StructureSessionContextService()
        );

        hopitalA = structure(1L, "Hopital A", TypeStructure.HOPITAL, "Rue A", "Lome", "Maritime", "1111");
        cliniqueB = structure(2L, "Clinique B", TypeStructure.CLINIQUE, "Rue B", "Kara", "Kara", "2222");
        centreSansAdresse = structure(3L, "Centre Sans Adresse", TypeStructure.CENTRE_DE_SANTE, null, "Atakpame", "Plateaux", null);
        pharmacieCentrale = pharmacie(10L, "Pharmacie Centrale", "Boulevard Central", "Centre", "Lome", "Maritime", "3333");
        pharmacieAgoe = pharmacie(11L, "Pharmacie Agoe", "Rue Agoe", "Agoe", "Lome", "Maritime", "4444");
        pharmacieSansTelephone = pharmacie(12L, "Pharmacie Sans Telephone", "Rue Silence", "Nyekonakpoe", "Lome", "Maritime", null);
        Pharmacie bonPasteur = pharmacie(13L, "Pharmacie Bon Pasteur", "Adidogome", "Adidogome", "Lome", "Maritime", "22 21 13 67");
        Pharmacie elNissi = pharmacie(14L, "Pharmacie El-Nissi", "Agoe", "Agoe", "Lome", "Maritime", "99 73 39 32");
        pharmacieCentraleOfficielle = officialPharmacy(10L, "010001", "Pharmacie Centrale", "Boulevard Central", "GRAND LOME", "PHARMACIE");
        StructureSante pharmacieAgoeOfficielle = officialPharmacy(11L, "010002", "Pharmacie Agoe", "Rue Agoe", "GRAND LOME", "PHARMACIE");
        StructureSante pharmacieSansTelephoneOfficielle = officialPharmacy(12L, "010003", "Pharmacie Sans Telephone", "Rue Silence", "GRAND LOME", "PHARMACIE");
        pharmacieBonPasteurOfficielle = officialPharmacy(13L, "010004", "Pharmacie Bon Pasteur", "Adidogome", "GRAND LOME", "PHARMACIE");
        pharmacieElNissiOfficielle = officialPharmacy(14L, "010005", "Pharmacie El-Nissi", "Agoe", "GRAND LOME", "PHARMACIE");
        consultation = prestation(20L, "CONSULTATION_GENERALE", "Consultation générale", 80.0);
        doliprane = medicament(30L, "0002601", "DOLIPRANE COMP 500MG");

        List<StructureSante> structures = List.of(hopitalA, cliniqueB, centreSansAdresse);
        List<Pharmacie> pharmacies = List.of(pharmacieCentrale, pharmacieAgoe, pharmacieSansTelephone, bonPasteur, elNissi);
        lenient().when(structureSanteRepository.findByAgrementAMUTrue()).thenReturn(structures);
        lenient().when(structureSanteRepository.findById(1L)).thenReturn(Optional.of(hopitalA));
        lenient().when(structureSanteRepository.findById(2L)).thenReturn(Optional.of(cliniqueB));
        lenient().when(structureSanteRepository.findById(3L)).thenReturn(Optional.of(centreSansAdresse));
        lenient().when(structureSanteRepository.findOfficialPharmacies(any(Sort.class))).thenReturn(List.of(
                pharmacieCentraleOfficielle,
                pharmacieAgoeOfficielle,
                pharmacieSansTelephoneOfficielle,
                pharmacieBonPasteurOfficielle,
                pharmacieElNissiOfficielle
        ));
        lenient().when(structureSanteRepository.findById(10L)).thenReturn(Optional.of(pharmacieCentraleOfficielle));
        lenient().when(structureSanteRepository.findById(11L)).thenReturn(Optional.of(pharmacieAgoeOfficielle));
        lenient().when(structureSanteRepository.findById(12L)).thenReturn(Optional.of(pharmacieSansTelephoneOfficielle));
        lenient().when(structureSanteRepository.findById(13L)).thenReturn(Optional.of(pharmacieBonPasteurOfficielle));
        lenient().when(structureSanteRepository.findById(14L)).thenReturn(Optional.of(pharmacieElNissiOfficielle));
        lenient().when(pharmacieRepository.findAll()).thenReturn(pharmacies);
        lenient().when(pharmacieRepository.findById(10L)).thenReturn(Optional.of(pharmacieCentrale));
        lenient().when(pharmacieRepository.findById(11L)).thenReturn(Optional.of(pharmacieAgoe));
        lenient().when(pharmacieRepository.findById(12L)).thenReturn(Optional.of(pharmacieSansTelephone));
        lenient().when(pharmacieRepository.findById(13L)).thenReturn(Optional.of(bonPasteur));
        lenient().when(pharmacieRepository.findById(14L)).thenReturn(Optional.of(elNissi));

        lenient().when(prestationRepository.findAll()).thenReturn(List.of(consultation));
        lenient().when(prestationRepository.findByPrisEnChargeTrue()).thenReturn(List.of(consultation));
        lenient().when(prestationRepository.findById(20L)).thenReturn(Optional.of(consultation));
        lenient().when(prestationRepository.findByNomActeContainingIgnoreCase(anyString()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0)).contains("consultation")
                        ? List.of(consultation)
                        : List.of());

        lenient().when(faqChatbotService.answer(any())).thenReturn(Optional.empty());
        lenient().when(aiAgentService.answer(any())).thenReturn(ChatbotResponseDTO.builder()
                .intent("FALLBACK")
                .found(false)
                .message("Fallback test")
                .sources(List.of())
                .build());
        lenient().when(medicationKnowledgeService.isListRequest(anyString())).thenReturn(false);
        lenient().when(medicamentRepository.findById(30L)).thenReturn(Optional.of(doliprane));
        lenient().when(medicamentRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        lenient().when(medicamentRepository.findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc(anyString())).thenReturn(List.of());
        lenient().when(medicamentRepository.findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc(anyString())).thenReturn(List.of());
        lenient().when(medicamentRepository.searchForChatbot(anyString(), any(Pageable.class))).thenReturn(List.of());
        lenient().when(medicamentRepository.findByNomIgnoreCaseAndActifTrue("doliprane comp 500mg")).thenReturn(Optional.of(doliprane));
    }

    @Test
    void remembersUniqueStructureAndAnswersAddressFromRepository() {
        ChatbotResponseDTO first = ask("session-a", "Je cherche Hopital A");
        ChatbotResponseDTO followUp = ask("session-a", "Quelle est son adresse ?");

        assertThat(first.getMessage()).contains("Hopital A");
        assertThat(followUp.getMessage()).isEqualTo("Adresse : Rue A.");
        verify(structureSanteRepository).findById(1L);
    }

    @Test
    void answersContextualPharmacyPhoneFromRepository() {
        ask("session-a", "Je cherche Pharmacie Centrale");

        ChatbotResponseDTO followUp = ask("session-a", "Et son téléphone ?");

        assertThat(followUp.getMessage()).isEqualTo("Telephone non renseigne dans le referentiel officiel.");
        verify(structureSanteRepository).findById(10L);
    }

    @Test
    void answersPrecisePharmacyAddressWithoutExistingContext() {
        ChatbotResponseDTO response = ask("session-a", "Ou se trouve la pharmacie Bon Pasteur ?");

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Adresse : Adidogome.");
    }

    @Test
    void answersPrecisePharmacyPhoneWithoutExistingContext() {
        ChatbotResponseDTO response = ask("session-a", "Quel est le numero de la pharmacie El-Nissi ?");

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Telephone non renseigne dans le referentiel officiel.");
    }

    @Test
    void asksForStructureWhenNoContextExists() {
        ChatbotResponseDTO response = ask("session-empty", "Quelle est son adresse ?");

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Veuillez preciser la structure de sante concernee");
    }

    @Test
    void multiplePharmaciesDoNotCreateContext() {
        ChatbotResponseDTO list = ask("session-a", "Je cherche une pharmacie conventionnée à Lomé.");
        ChatbotResponseDTO followUp = ask("session-a", "Quelle est son adresse ?");

        assertThat(list.getSources()).hasSize(5);
        assertThat(followUp.getFound()).isFalse();
        assertThat(followUp.getMessage()).contains("Veuillez preciser la structure de sante concernee");
    }

    @Test
    void unknownStructureDoesNotCreateContext() {
        ChatbotResponseDTO unknown = ask("session-a", "Je cherche Hopital Introuvable");
        ChatbotResponseDTO followUp = ask("session-a", "Quelle est son adresse ?");

        assertThat(unknown.getFound()).isFalse();
        assertThat(followUp.getFound()).isFalse();
        assertThat(followUp.getMessage()).contains("Veuillez preciser la structure de sante concernee");
    }

    @Test
    void changesStructureContextAfterAnotherUniqueStructure() {
        ask("session-a", "Je cherche Hopital A");
        ask("session-a", "Je cherche Clinique B");

        ChatbotResponseDTO followUp = ask("session-a", "Quelle est son adresse ?");

        assertThat(followUp.getMessage()).isEqualTo("Adresse : Rue B.");
    }

    @Test
    void separatesStructureContextBySessionId() {
        ask("session-a", "Je cherche Hopital A");
        ask("session-b", "Je cherche Pharmacie Centrale");

        assertThat(ask("session-a", "Quelle est son adresse ?").getMessage()).isEqualTo("Adresse : Rue A.");
        assertThat(ask("session-b", "Quelle est son adresse ?").getMessage()).isEqualTo("Adresse : Boulevard Central.");
    }

    @Test
    void coexistsWithMedicationAndPrestationContextsWithoutStealingRateFollowUpForStructure() {
        ask("session-a", "Quel est le prix de DOLIPRANE COMP 500MG ?");
        ask("session-a", "Les consultations générales sont-elles prises en charge ?");
        ask("session-a", "Je cherche Pharmacie Centrale");

        ChatbotResponseDTO medicationFollowUp = ask("session-a", "Et son taux ?");
        ChatbotResponseDTO structureFollowUp = ask("session-a", "Quelle est son adresse ?");

        assertThat(medicationFollowUp.getMessage()).contains("Consultation générale").contains("80%");
        assertThat(medicationFollowUp.getMessage()).doesNotContain("Pharmacie Centrale");
        assertThat(structureFollowUp.getMessage()).isEqualTo("Adresse : Boulevard Central.");
    }

    @Test
    void reportsMissingAddressAndPhoneWithoutInventingValues() {
        ask("session-a", "Je cherche Centre Sans Adresse");

        assertThat(ask("session-a", "Quelle est son adresse ?").getMessage()).isEqualTo("Adresse : adresse non renseignee.");
        assertThat(ask("session-a", "Quel est son téléphone ?").getMessage()).isEqualTo("Telephone : non disponible.");
    }

    @Test
    void answersRegionCityAndAgreementForContextualStructure() {
        ask("session-a", "Je cherche Hopital A");

        assertThat(ask("session-a", "Dans quelle région se trouve-t-elle ?").getMessage()).isEqualTo("Region : Maritime.");
        assertThat(ask("session-a", "Dans quelle ville ?").getMessage()).isEqualTo("Ville : Lome.");
        assertThat(ask("session-a", "Est-elle conventionnée ?").getMessage()).contains("Oui");
    }

    private ChatbotResponseDTO ask(String sessionId, String message) {
        return chatbotService.repondre(ChatbotRequestDTO.builder()
                .sessionId(sessionId)
                .message(message)
                .build());
    }

    private StructureSante structure(Long id, String name, TypeStructure type, String address, String city, String region, String phone) {
        return StructureSante.builder()
                .idStructure(id)
                .nom(name)
                .type(type)
                .adresse(address)
                .ville(city)
                .region(region)
                .telephone(phone)
                .agrementAMU(true)
                .actif(true)
                .build();
    }

    private Pharmacie pharmacie(Long id, String name, String address, String district, String city, String region, String phone) {
        return Pharmacie.builder()
                .id(id)
                .nom(name)
                .adresse(address)
                .quartier(district)
                .ville(city)
                .region(region)
                .telephone(phone)
                .agreee(true)
                .active(true)
                .build();
    }

    private StructureSante officialPharmacy(Long id, String code, String name, String address, String region, String typeOfficiel) {
        return StructureSante.builder()
                .idStructure(id)
                .code(code)
                .nom(name)
                .type(TypeStructure.PHARMACIE)
                .typeOfficiel(typeOfficiel)
                .adresse(address)
                .region(region)
                .agrementAMU(true)
                .actif(false)
                .build();
    }

    private Prestation prestation(Long id, String code, String name, Double rate) {
        return Prestation.builder()
                .idPrestation(id)
                .codeActe(code)
                .nomActe(name)
                .categorie(CategorieActe.CONSULTATION)
                .prisEnCharge(true)
                .tauxCouverture(rate)
                .build();
    }

    private Medicament medicament(Long id, String code, String name) {
        return Medicament.builder()
                .id(id)
                .code(code)
                .nom(name)
                .dci("Paracetamol")
                .dosage("500 mg")
                .formePharmaceutique("Comprime")
                .statut("REMBOURSABLE")
                .prixPublic(new BigDecimal("1000"))
                .baseRemboursement(new BigDecimal("800"))
                .tauxCouverture(80.0)
                .partInam(new BigDecimal("640"))
                .partBeneficiaire(new BigDecimal("160"))
                .prisEnCharge(true)
                .actif(true)
                .build();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
