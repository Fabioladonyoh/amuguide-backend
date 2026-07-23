package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.ChatbotSourceDTO;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ResponseGenerator {

    private static final List<String> DEFAULT_SUGGESTIONS = List.of("Consultation", "Radiologie", "Hopital proche");

    private final PrestationRepository prestationRepository;
    private final StructureSanteRepository structureSanteRepository;
    private final MedicationKnowledgeService medicationKnowledgeService;
    private final AiAgentService aiAgentService;

    public ChatbotResponseDTO generate(IntentDetectionResult detection) {
        return switch (detection.getIntent()) {
            case GREETING -> greeting();
            case AMU_INFO -> amuInfo();
            case COVERAGE -> coverage(detection);
            case MEDICATION_SEARCH -> medicationSearch(detection);
            case HOSPITAL_SEARCH -> hospitalSearch(detection);
            case PROCEDURE -> procedure(detection);
            case AGENT_ASSISTED -> fallback(detection);
            case FALLBACK -> fallback(detection);
        };
    }

    private ChatbotResponseDTO greeting() {
        return base(ChatIntent.GREETING, "Bonjour. Comment puis-je vous aider sur l'AMU ?", DEFAULT_SUGGESTIONS);
    }

    private ChatbotResponseDTO amuInfo() {
        return base(ChatIntent.AMU_INFO,
                "L'AMU, ou Assurance Maladie Universelle, est un dispositif qui facilite l'acces aux soins de sante en prenant en charge une partie des frais medicaux des assures. Elle peut couvrir des prestations comme les consultations, l'hospitalisation, certains medicaments, la radiologie ou les examens, selon les taux et conditions definis.\n\nAvec AMU Guide, vous pouvez verifier si une prestation est couverte, connaitre le taux de couverture, voir les documents requis et rechercher une structure de sante agreee.",
                List.of("Consultation couverte ?", "Documents requis", "Hopital proche"))
                .toBuilder()
                .sources(List.of(backendSource("INFORMATION_AMU", "Informations generales AMU")))
                .build();
    }

    private ChatbotResponseDTO coverage(IntentDetectionResult detection) {
        Prestation prestation = findPrestation(detection.getKeyword(), detection.getNormalizedMessage());
        if (prestation == null) {
            if (isGeneralCoverageQuestion(detection.getNormalizedMessage())) {
                return coveredPrestationsSummary();
            }
            return aiAgentService.answer(detection);
        }

        String message = prestation.getPrisEnCharge()
                ? String.format(Locale.FRANCE, "Oui. %s est prise en charge a %.0f%%.", prestation.getNomActe(), prestation.getTauxCouverture())
                : String.format("%s n'est pas prise en charge par l'AMU.", prestation.getNomActe());

        return ChatbotResponseDTO.builder()
                .statut(prestation.getPrisEnCharge() ? "COUVERT" : "NON_COUVERT")
                .intent(ChatIntent.COVERAGE.name())
                .message(message)
                .codeActe(prestation.getCodeActe())
                .nomActe(prestation.getNomActe())
                .prisEnCharge(prestation.getPrisEnCharge())
                .tauxCouverture(prestation.getTauxCouverture())
                .conditionsPriseEnCharge(prestation.getConditionsPriseEnCharge())
                .documentsRequis(prestation.getDocumentsRequis())
                .sources(List.of(prestationSource(prestation)))
                .suggestionList(List.of("Documents requis", "Hopital agree", "Autre prestation"))
                .suggestions("Suggestions : Documents requis, Hopital agree, Autre prestation")
                .build();
    }

    private ChatbotResponseDTO hospitalSearch(IntentDetectionResult detection) {
        String normalizedMessage = detection.getNormalizedMessage();
        String normalizedCity = normalizeText(detection.getCity());
        boolean pharmacyRequested = normalizedMessage.contains("pharmacie");
        boolean hospitalRequested = normalizedMessage.contains("hopital") || normalizedMessage.contains("chu");
        boolean clinicRequested = normalizedMessage.contains("clinique");
        boolean centerRequested = normalizedMessage.contains("centre");
        boolean nearMeRequested = normalizedMessage.contains("pres de moi") || normalizedMessage.contains("proche de moi");

        if (nearMeRequested && detection.getLatitude() == null && detection.getLongitude() == null) {
            return base(ChatIntent.HOSPITAL_SEARCH,
                    "Pour rechercher une structure pres de vous, veuillez autoriser la geolocalisation ou preciser votre ville.",
                    List.of("Clinique a Lome", "Hopital a Kara", "Pharmacie agreee"))
                    .toBuilder()
                    .found(false)
                    .build();
        }

        List<StructureSante> structures = structureSanteRepository.findByAgrementAMUTrue().stream()
                .filter(structure -> detection.getCity() == null || sameNormalizedCity(structure.getVille(), normalizedCity))
                .filter(structure -> !pharmacyRequested || structure.getType() != null
                        && "PHARMACIE".equals(structure.getType().name()))
                .filter(structure -> !hospitalRequested || structure.getType() != null
                        && "HOPITAL".equals(structure.getType().name()))
                .filter(structure -> !clinicRequested || structure.getType() != null
                        && "CLINIQUE".equals(structure.getType().name()))
                .filter(structure -> !centerRequested || structure.getType() != null
                        && "CENTRE_DE_SANTE".equals(structure.getType().name()))
                .sorted((left, right) -> compareByDistanceWhenAvailable(left, right, detection))
                .toList();

        if (structures.isEmpty()) {
            String location = detection.getCity() == null ? "" : " a " + detection.getCity();
            return base(ChatIntent.HOSPITAL_SEARCH,
                    "Aucune structure agreee trouvee" + location + ". Essayez une autre ville.",
                    List.of("Lome", "Kara", "Consultation"))
                    .toBuilder()
                    .found(false)
                    .build();
        }

        String title = detection.getCity() == null
                ? "Structures agreees trouvees :"
                : "Structures trouvees a " + detection.getCity() + " :";
        String names = structures.stream()
                .sorted(Comparator.comparing(StructureSante::getNom))
                .limit(5)
                .map(structure -> "- " + structure.getNom() + " (" + structure.getType() + ")")
                .reduce("", (left, right) -> left + "\n" + right);

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.HOSPITAL_SEARCH.name())
                .message(title + names + "\n\nVoulez-vous afficher la carte ?")
                .sources(structures.stream()
                        .sorted(Comparator.comparing(StructureSante::getNom))
                        .limit(5)
                        .map(this::structureSource)
                        .toList())
                .suggestionList(List.of("Afficher la carte", "Consultation", "Autre ville"))
                .suggestions("Suggestions : Afficher la carte, Consultation, Autre ville")
                .build();
    }

    private ChatbotResponseDTO medicationSearch(IntentDetectionResult detection) {
        if (medicationKnowledgeService.isListRequest(detection.getNormalizedMessage())) {
            List<String> medications = medicationKnowledgeService.list(25);
            String items = medications.stream()
                    .map(match -> "- " + match)
                    .reduce("", (left, right) -> left + "\n" + right);

            return base(ChatIntent.MEDICATION_SEARCH,
                    "Voici les premiers medicaments et dispositifs extraits du referentiel School AMU valide a partir du 01/01/2025. Le fichier contient "
                            + medicationKnowledgeService.count()
                            + " lignes extraites :"
                            + items
                            + "\n\nLa liste complete est longue. Donnez le nom d'un medicament, par exemple paracetamol, amoxicilline ou ibuprofene, pour une recherche precise.",
                    List.of("Paracetamol", "Amoxicilline", "Ibuprofene"))
                    .toBuilder()
                    .sources(List.of(referentielMedicationSource()))
                    .build();
        }

        List<String> matches = medicationKnowledgeService.search(detection.getNormalizedMessage(), 5);
        if (matches.isEmpty()) {
            return base(ChatIntent.MEDICATION_SEARCH,
                    "Je n'ai pas trouve ce medicament dans le referentiel actuellement enregistre. Verifiez l'orthographe ou indiquez le nom du principe actif.",
                    List.of("Paracetamol", "Amoxicilline", "Ibuprofene"))
                    .toBuilder()
                    .found(false)
                    .sources(List.of(referentielMedicationSource()))
                    .build();
        }

        String brandNote = medicationKnowledgeService.resolveBrand(detection.getNormalizedMessage());
        String items = matches.stream()
                .map(match -> "- " + match)
                .reduce("", (left, right) -> left + "\n" + right);

        return base(ChatIntent.MEDICATION_SEARCH,
                (brandNote == null ? "" : brandNote + ".\n\n")
                        + "J'ai trouve ces correspondances dans le referentiel des medicaments et dispositifs medicaux School AMU, valide a partir du 01/01/2025 :"
                        + items
                        + "\n\nJe peux donc confirmer que le principe actif correspondant est present dans le referentiel. En revanche, le document extrait ne me permet pas de confirmer la marque commerciale exacte ni une condition individuelle INAM. Pour la delivrance, l'ordonnance et la pharmacie/structure agreee restent importantes.",
                List.of("Documents requis", "Pharmacie agreee", "Autre medicament"))
                .toBuilder()
                .sources(List.of(referentielMedicationSource()))
                .build();
    }

    private ChatbotResponseDTO procedure(IntentDetectionResult detection) {
        if (isGeneralProcedureQuestion(detection.getNormalizedMessage())) {
            return generalProcedure(detection);
        }

        Prestation prestation = findPrestation(detection.getKeyword(), detection.getNormalizedMessage());
        if (prestation == null) {
            return generalProcedure(detection);
        }

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.PROCEDURE.name())
                .message("Documents requis pour " + prestation.getNomActe() + " : " + prestation.getDocumentsRequis()
                        + "\nConditions : " + prestation.getConditionsPriseEnCharge())
                .codeActe(prestation.getCodeActe())
                .nomActe(prestation.getNomActe())
                .prisEnCharge(prestation.getPrisEnCharge())
                .tauxCouverture(prestation.getTauxCouverture())
                .conditionsPriseEnCharge(prestation.getConditionsPriseEnCharge())
                .documentsRequis(prestation.getDocumentsRequis())
                .sources(List.of(prestationSource(prestation)))
                .suggestionList(List.of("Taux couverture", "Hopital agree", "Autre prestation"))
                .suggestions("Suggestions : Taux couverture, Hopital agree, Autre prestation")
                .build();
    }

    private ChatbotResponseDTO fallback(IntentDetectionResult detection) {
        return aiAgentService.answer(detection)
                .toBuilder()
                .found(false)
                .build();
    }

    private boolean isGeneralProcedureQuestion(String normalizedMessage) {
        return normalizedMessage.contains("renouvel")
                || normalizedMessage.contains("utiliser")
                || normalizedMessage.contains("beneficier")
                || normalizedMessage.contains("que faut il");
    }

    private ChatbotResponseDTO generalProcedure(IntentDetectionResult detection) {
        String normalizedMessage = detection.getNormalizedMessage();

        if (normalizedMessage.contains("renouvel")
                || normalizedMessage.contains("refaire")
                || normalizedMessage.contains("perte")
                || normalizedMessage.contains("expire")) {
            return procedureResponse(
                    "Renouvellement de la carte AMU",
                    "Pour renouveler votre carte AMU :\n1. Presentez votre ancienne carte AMU.\n2. Fournissez une piece d'identite valide.\n3. Deposez la demande aupres du service competent.\n4. Suivez les instructions donnees pour le retrait de la nouvelle carte.",
                    List.of("Utiliser ma carte", "Documents requis", "Beneficier d'une prestation"));
        }

        if (normalizedMessage.contains("utiliser") && normalizedMessage.contains("carte")) {
            return procedureResponse(
                    "Utilisation de la carte AMU",
                    "Pour utiliser votre carte AMU :\n1. Presentez votre carte AMU valide dans une structure agreee.\n2. Indiquez la prestation souhaitee.\n3. Fournissez les documents medicaux demandes si la prestation l'exige.\n4. La structure applique la prise en charge selon le taux enregistre.",
                    List.of("Structure agreee", "Consultation", "Documents requis"));
        }

        if (normalizedMessage.contains("beneficier") || normalizedMessage.contains("que faut il")) {
            return procedureResponse(
                    "Beneficier d'une prestation AMU",
                    "Pour beneficier d'une prestation AMU :\n1. Avoir un compte assure AMU actif.\n2. Presenter une carte AMU valide.\n3. Consulter une structure de sante agreee.\n4. Respecter les conditions et documents requis pour la prestation demandee.",
                    List.of("Prestations couvertes", "Structures agreees", "Documents requis"));
        }

        return procedureResponse(
                "Documents AMU",
                "Les documents a fournir dependent de la prestation. De maniere generale, gardez votre carte AMU valide, une piece d'identite et les documents medicaux disponibles. Pour une reponse precise, indiquez la prestation, par exemple consultation, radiologie ou hospitalisation.",
                List.of("Consultation", "Radiologie", "Hospitalisation"));
    }

    private ChatbotResponseDTO procedureResponse(String title, String message, List<String> suggestions) {
        return base(ChatIntent.PROCEDURE, message, suggestions)
                .toBuilder()
                .sources(List.of(backendSource("PROCEDURE", title)))
                .build();
    }

    private Prestation findPrestation(String keyword, String normalizedMessage) {
        if (keyword != null) {
            List<Prestation> matches = prestationRepository.findByNomActeContainingIgnoreCase(keyword);
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
        }

        String normalizedKeyword = normalizeText(keyword);
        return prestationRepository.findAll().stream()
                .filter(prestation -> containsPrestationText(prestation, normalizedMessage, normalizedKeyword))
                .findFirst()
                .orElse(null);
    }

    private boolean containsPrestationText(Prestation prestation, String normalizedMessage, String normalizedKeyword) {
        List<String> values = List.of(
                normalizeText(prestation.getNomActe()),
                normalizeText(prestation.getCodeActe()),
                normalizeText(prestation.getCategorie() == null ? null : prestation.getCategorie().name()),
                normalizeText(prestation.getDescription()),
                normalizeText(prestation.getConditionsPriseEnCharge()),
                normalizeText(prestation.getDocumentsRequis())
        );

        return values.stream().anyMatch(value -> !value.isBlank()
                && (normalizedMessage.contains(value)
                || value.contains(normalizedMessage)
                || !normalizedKeyword.isBlank() && value.contains(normalizedKeyword)));
    }

    private boolean isGeneralCoverageQuestion(String normalizedMessage) {
        return normalizedMessage.contains("prestation")
                || normalizedMessage.contains("prestations")
                || normalizedMessage.contains("couvert")
                || normalizedMessage.contains("couverture")
                || normalizedMessage.contains("prise en charge");
    }

    private ChatbotResponseDTO coveredPrestationsSummary() {
        List<Prestation> prestations = prestationRepository.findAll().stream()
                .filter(prestation -> Boolean.TRUE.equals(prestation.getPrisEnCharge()))
                .sorted(Comparator.comparing(Prestation::getNomActe))
                .limit(6)
                .toList();

        if (prestations.isEmpty()) {
            return base(ChatIntent.COVERAGE,
                    "Je n'ai pas trouve de prestation couverte dans la base pour le moment.",
                    List.of("Consultation", "Radiologie", "Hospitalisation"));
        }

        String items = prestations.stream()
                .map(prestation -> String.format(Locale.FRANCE, "- %s : %.0f%%",
                        prestation.getNomActe(),
                        prestation.getTauxCouverture()))
                .reduce("", (left, right) -> left + "\n" + right);

        return ChatbotResponseDTO.builder()
                .statut("COUVERT")
                .intent(ChatIntent.COVERAGE.name())
                .message("Voici quelques prestations couvertes par l'AMU :" + items
                        + "\n\nIndiquez une prestation precise pour connaitre les conditions et documents requis.")
                .sources(prestations.stream()
                        .map(this::prestationSource)
                        .toList())
                .suggestionList(List.of("Consultation", "Radiologie", "Hospitalisation"))
                .suggestions("Suggestions : Consultation, Radiologie, Hospitalisation")
                .build();
    }

    private boolean sameNormalizedCity(String structureCity, String normalizedCity) {
        String normalizedStructureCity = normalizeText(structureCity);
        return normalizedStructureCity.equals(normalizedCity)
                || normalizedStructureCity.contains(normalizedCity)
                || normalizedCity.contains(normalizedStructureCity);
    }

    private int compareByDistanceWhenAvailable(StructureSante left, StructureSante right, IntentDetectionResult detection) {
        if (detection.getLatitude() == null || detection.getLongitude() == null) {
            return left.getNom().compareToIgnoreCase(right.getNom());
        }

        return Double.compare(
                distance(left, detection.getLatitude(), detection.getLongitude()),
                distance(right, detection.getLatitude(), detection.getLongitude())
        );
    }

    private double distance(StructureSante structure, double latitude, double longitude) {
        if (structure.getLatitude() == null || structure.getLongitude() == null) {
            return Double.MAX_VALUE;
        }

        double latitudeDelta = structure.getLatitude() - latitude;
        double longitudeDelta = structure.getLongitude() - longitude;
        return Math.sqrt(latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private ChatbotResponseDTO base(ChatIntent intent, String message, List<String> suggestions) {
        return ChatbotResponseDTO.builder()
                .intent(intent.name())
                .found(true)
                .message(message)
                .suggestionList(suggestions)
                .suggestions("Suggestions : " + String.join(", ", suggestions))
                .build();
    }

    private ChatbotSourceDTO prestationSource(Prestation prestation) {
        return ChatbotSourceDTO.builder()
                .type("PRESTATION")
                .id(prestation.getIdPrestation())
                .title(prestation.getNomActe())
                .build();
    }

    private ChatbotSourceDTO structureSource(StructureSante structure) {
        return ChatbotSourceDTO.builder()
                .type("STRUCTURE")
                .id(structure.getIdStructure())
                .title(structure.getNom())
                .build();
    }

    private ChatbotSourceDTO referentielMedicationSource() {
        return ChatbotSourceDTO.builder()
                .type("REFERENTIEL_MEDICAMENT")
                .title("Référentiel médicaments School AMU 2025")
                .build();
    }

    private ChatbotSourceDTO backendSource(String type, String title) {
        return ChatbotSourceDTO.builder()
                .type(type)
                .title(title)
                .build();
    }
}
