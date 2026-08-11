package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.entity.Pharmacie;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ResponseGeneratorStructureSearchTest {

    @Mock
    private PrestationRepository prestationRepository;

    @Mock
    private StructureSanteRepository structureSanteRepository;

    @Mock
    private PharmacieRepository pharmacieRepository;

    @Mock
    private MedicationKnowledgeService medicationKnowledgeService;

    @Mock
    private MedicamentChatbotSearchService medicamentChatbotSearchService;

    @Mock
    private HospitalisationTarifChatbotService hospitalisationTarifChatbotService;

    @Mock
    private AiAgentService aiAgentService;

    private ResponseGenerator responseGenerator;

    @BeforeEach
    void setUp() {
        responseGenerator = new ResponseGenerator(
                prestationRepository,
                structureSanteRepository,
                pharmacieRepository,
                medicationKnowledgeService,
                medicamentChatbotSearchService,
                hospitalisationTarifChatbotService,
                aiAgentService
        );

        lenient().when(structureSanteRepository.findByAgrementAMUTrue()).thenReturn(List.of(
                structure(1L, "CHU Sylvanus Olympio", TypeStructure.HOPITAL, "Rue Hopital", "Lome", "Maritime", "22210000", 6.13, 1.22),
                structure(2L, "Clinique AMU Agoe", TypeStructure.CLINIQUE, "Agoe", "Lome", "Maritime", "22220000", 6.20, 1.20),
                structure(3L, "Centre de sante Atakpame", TypeStructure.CENTRE_DE_SANTE, null, "Atakpame", "Plateaux", null, 7.53, 1.13),
                structure(4L, "Hopital Kara", TypeStructure.HOPITAL, "Centre-ville", "Kara", "Kara", "22230000", 9.55, 1.18)
        ));
        lenient().when(pharmacieRepository.findAll()).thenReturn(List.of(
                pharmacie(1L, "Pharmacie Centrale", "Boulevard", "Centre", "Lome", "Maritime", "22240000", 6.12, 1.21),
                pharmacie(2L, "Pharmacie des Plateaux", null, "Adeta", "Kpalime", "Plateaux", null, 6.90, 0.63),
                pharmacie(3L, "Pharmacie Kara", "Route nationale", "Kara", "Kara", "Kara", "22250000", 9.56, 1.19),
                pharmacie(4L, "Pharmacie Bon Pasteur", "Adidogome", "Adidogome", "Lome", "Maritime", "22 21 13 67", null, null),
                pharmacie(5L, "Pharmacie El-Nissi", "Agoe", "Agoe", "Lome", "Maritime", "99 73 39 32", null, null),
                pharmacie(6L, "Pharmacie Bon Secours", "Rue Bon Secours", "Tokoin", "Lome", "Maritime", "22222222", null, null)
        ));
        lenient().when(structureSanteRepository.findOfficialPharmacies(any(Sort.class))).thenReturn(List.of(
                officialPharmacy(10L, "010001", "Pharmacie Centrale", "PHARMACIE", "Boulevard", "Maritime"),
                officialPharmacy(11L, "010002", "Pharmacie des Plateaux", "PHARMACIE", null, "Plateaux"),
                officialPharmacy(12L, "010003", "Depot pharmacie Agoe", "DEPOT PHARMACIE", "Agoe", "Grand Lome"),
                officialPharmacy(13L, "010004", "Pharmacie Bon Pasteur", "PHARMACIE", "Adidogome", "Maritime"),
                officialPharmacy(14L, "010005", "Pharmacie El-Nissi", "PHARMACIE", "Agoe", "Maritime"),
                officialPharmacy(15L, "010006", "Pharmacie Bon Secours", "PHARMACIE", "Rue Bon Secours", "Maritime")
        ));
    }

    @Test
    void listsConventionedStructuresWithoutSelectingAnArbitraryOne() {
        ChatbotResponseDTO response = response("Quelles sont les structures conventionnees ?");

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).contains("Structures agreees trouvees");
        assertThat(response.getMessage()).contains("CHU Sylvanus Olympio");
        assertThat(response.getMessage()).contains("Clinique AMU Agoe");
        assertThat(response.getMessage()).contains("Centre de sante Atakpame");
        assertThat(response.getSources()).hasSize(4);
    }

    @Test
    void filtersHospitalsUsingStructureTypeField() {
        ChatbotResponseDTO response = response("Quels hopitaux sont conventionnes ?");

        assertThat(response.getMessage()).contains("CHU Sylvanus Olympio");
        assertThat(response.getMessage()).contains("Hopital Kara");
        assertThat(response.getMessage()).doesNotContain("Clinique AMU Agoe");
        assertThat(response.getMessage()).doesNotContain("Centre de sante Atakpame");
    }

    @Test
    void filtersCentersUsingStructureTypeFieldAndKeepsMissingAddressExplicit() {
        ChatbotResponseDTO response = response("Montre-moi les centres conventionnes.");

        assertThat(response.getMessage()).contains("Centre de sante Atakpame");
        assertThat(response.getMessage()).contains("Adresse : adresse non renseignee");
        assertThat(response.getMessage()).contains("Telephone : non disponible");
        assertThat(response.getMessage()).doesNotContain("null");
    }

    @Test
    void searchesPharmaciesFromOfficialStructureRepository() {
        ChatbotResponseDTO response = response("Quelles pharmacies sont conventionnees ?");

        assertThat(response.getMessage()).contains("Pharmacies agreees trouvees");
        assertThat(response.getMessage()).contains("Pharmacie Centrale");
        assertThat(response.getMessage()).contains("Pharmacie des Plateaux");
        assertThat(response.getMessage()).contains("Depot pharmacie Agoe");
        assertThat(response.getSources()).extracting("type").containsOnly("PHARMACIE");
    }

    @Test
    void filtersStructuresByCity() {
        ChatbotResponseDTO response = response("Quelles structures sont disponibles a Lome ?");

        assertThat(response.getMessage()).contains("Structures trouvees a Lome");
        assertThat(response.getMessage()).contains("CHU Sylvanus Olympio");
        assertThat(response.getMessage()).contains("Clinique AMU Agoe");
        assertThat(response.getMessage()).doesNotContain("Hopital Kara");
    }

    @Test
    void filtersHospitalsByRegionFromReferenceData() {
        ChatbotResponseDTO response = response("Quels hopitaux sont disponibles dans la region Maritime ?");

        assertThat(response.getMessage()).contains("CHU Sylvanus Olympio");
        assertThat(response.getMessage()).contains("Region : Maritime");
        assertThat(response.getMessage()).doesNotContain("Hopital Kara");
        assertThat(response.getMessage()).doesNotContain("Clinique AMU Agoe");
    }

    @Test
    void filtersPharmaciesByRegionFromReferenceData() {
        ChatbotResponseDTO response = response("Quelles pharmacies sont conventionnees dans les Plateaux ?");

        assertThat(response.getMessage()).contains("Pharmacie des Plateaux");
        assertThat(response.getMessage()).contains("Region : Plateaux");
        assertThat(response.getMessage()).contains("Adresse : adresse non renseignee");
        assertThat(response.getMessage()).doesNotContain("Pharmacie Centrale");
    }

    @Test
    void sortsByCoordinatesWhenRequestContainsGeolocation() {
        ChatbotResponseDTO response = responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH)
                .normalizedMessage(normalize("Quelles structures sont proches de moi ?"))
                .latitude(9.54)
                .longitude(1.18)
                .score(0.95)
                .build());

        assertThat(response.getMessage()).contains("Structures agreees trouvees");
        assertThat(response.getSources()).first().extracting("title").isEqualTo("Hopital Kara");
    }

    @Test
    void limitsLongResultListAndMentionsMoreResults() {
        lenient().when(structureSanteRepository.findByAgrementAMUTrue()).thenReturn(List.of(
                structure(1L, "Structure 1", TypeStructure.HOPITAL, "A1", "Lome", "Maritime", "1", null, null),
                structure(2L, "Structure 2", TypeStructure.CLINIQUE, "A2", "Lome", "Maritime", "2", null, null),
                structure(3L, "Structure 3", TypeStructure.CENTRE_DE_SANTE, "A3", "Lome", "Maritime", "3", null, null),
                structure(4L, "Structure 4", TypeStructure.HOPITAL, "A4", "Kara", "Kara", "4", null, null),
                structure(5L, "Structure 5", TypeStructure.CLINIQUE, "A5", "Sokode", "Centrale", "5", null, null),
                structure(6L, "Structure 6", TypeStructure.HOPITAL, "A6", "Dapaong", "Savanes", "6", null, null)
        ));

        ChatbotResponseDTO response = response("Quelles sont les structures conventionnees ?");

        assertThat(response.getSources()).hasSize(5);
        assertThat(response.getMessage()).contains("6 resultats trouves");
        assertThat(response.getMessage()).doesNotContain("Structure 6");
    }

    @Test
    void returnsNotFoundWithoutInventingData() {
        ChatbotResponseDTO response = response("Quelles pharmacies sont conventionnees dans les Golfe-Nord ?", "Golfe Nord");

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Aucune pharmacie agreee trouvee a Golfe Nord");
    }

    @Test
    void answersPrecisePharmacyAddressWithoutInventingOtherData() {
        ChatbotResponseDTO response = response("Ou se trouve la pharmacie Bon Pasteur ?");

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Adresse : Adidogome.");
        assertThat(response.getSources()).extracting("title").containsExactly("Pharmacie Bon Pasteur");
    }

    @Test
    void answersPrecisePharmacyPhoneWithoutAskingForContext() {
        ChatbotResponseDTO response = response("Quel est le numero de la pharmacie El-Nissi ?");

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Telephone non renseigne dans le referentiel officiel.");
        assertThat(response.getSources()).extracting("title").containsExactly("Pharmacie El-Nissi");
    }

    @Test
    void reportsPreciseUnknownPharmacyWithoutInventingData() {
        ChatbotResponseDTO response = response("Ou se trouve la pharmacie Introuvable ?");

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Aucune pharmacie agreee trouvee");
        assertThat(response.getMessage()).doesNotContain("Telephone :");
    }

    @Test
    void reportsNullPharmacyAddressAndPhoneExplicitly() {
        assertThat(response("Ou se trouve la pharmacie des Plateaux ?").getMessage())
                .isEqualTo("Adresse : adresse non renseignee.");
        assertThat(response("Quel est le telephone de la pharmacie des Plateaux ?").getMessage())
                .isEqualTo("Telephone non renseigne dans le referentiel officiel.");
    }

    @Test
    void doesNotPromoteLegacyOnlyPharmacyAsOfficial() {
        ChatbotResponseDTO response = response("Ou se trouve la pharmacie Kara ?");

        assertThat(response.getFound()).isFalse();
        assertThat(response.getMessage()).contains("Aucune pharmacie agreee trouvee");
        assertThat(response.getSources()).isEmpty();
    }

    @Test
    void keepsAmbiguousPharmacySearchAsAList() {
        ChatbotResponseDTO response = response("Ou se trouve la pharmacie Bon ?");

        assertThat(response.getFound()).isTrue();
        assertThat(response.getMessage()).contains("Pharmacies agreees trouvees");
        assertThat(response.getMessage()).contains("Pharmacie Bon Pasteur");
        assertThat(response.getMessage()).contains("Pharmacie Bon Secours");
        assertThat(response.getMessage()).doesNotContain("Adresse : Adidogome.");
    }

    @Test
    void answersPreciseHospitalClinicAndCenterDetails() {
        assertThat(response("Ou se trouve l'hopital Sylvanus Olympio ?").getMessage())
                .isEqualTo("Adresse : Rue Hopital.");
        assertThat(response("Quel est le telephone de la clinique AMU Agoe ?").getMessage())
                .isEqualTo("Telephone : 22220000.");
        assertThat(response("Ou se trouve le centre de sante Atakpame ?").getMessage())
                .isEqualTo("Adresse : adresse non renseignee.");
    }

    private ChatbotResponseDTO response(String question) {
        return response(question, null);
    }

    private ChatbotResponseDTO response(String question, String city) {
        return responseGenerator.generate(IntentDetectionResult.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH)
                .normalizedMessage(normalize(question))
                .city(city)
                .score(0.95)
                .build());
    }

    private StructureSante structure(Long id, String name, TypeStructure type, String address, String city,
                                     String region, String phone, Double latitude, Double longitude) {
        return StructureSante.builder()
                .idStructure(id)
                .nom(name)
                .type(type)
                .adresse(address)
                .ville(city)
                .region(region)
                .telephone(phone)
                .latitude(latitude)
                .longitude(longitude)
                .agrementAMU(true)
                .actif(true)
                .build();
    }

    private Pharmacie pharmacie(Long id, String name, String address, String district, String city,
                                String region, String phone, Double latitude, Double longitude) {
        return Pharmacie.builder()
                .id(id)
                .nom(name)
                .adresse(address)
                .quartier(district)
                .ville(city)
                .region(region)
                .telephone(phone)
                .latitude(latitude)
                .longitude(longitude)
                .agreee(true)
                .active(true)
                .build();
    }

    private StructureSante officialPharmacy(Long id, String code, String name, String typeOfficiel, String address, String region) {
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
