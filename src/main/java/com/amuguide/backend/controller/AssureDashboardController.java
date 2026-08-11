package com.amuguide.backend.controller;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.dto.*;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.entity.ChatHistory;
import com.amuguide.backend.entity.Demande;
import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.enums.StatutAssure;
import com.amuguide.backend.enums.StatutDemande;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.exception.ConflictException;
import com.amuguide.backend.exception.ResourceNotFoundException;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.ChatHistoryRepository;
import com.amuguide.backend.repository.DemandeRepository;
import com.amuguide.backend.repository.MedicamentRepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import com.amuguide.backend.service.HospitalisationTarifService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/assure")
@RequiredArgsConstructor
public class AssureDashboardController {

    private final AssureAMURepository assureRepository;
    private final PrestationRepository prestationRepository;
    private final MedicamentRepository medicamentRepository;
    private final StructureSanteRepository structureRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final DemandeRepository demandeRepository;
    private final PasswordEncoder passwordEncoder;
    private final HospitalisationTarifService hospitalisationTarifService;

    @GetMapping("/dashboard")
    public AssureDashboardDTO dashboard() {
        AssureAMU assure = currentAssure();
        List<ChatHistory> chats = chatHistoryRepository.findByUser_IdAssureOrderByDateDesc(assure.getIdAssure());
        List<Demande> demandes = demandeRepository.findByAssure_IdAssure(assure.getIdAssure());
        long totalConversations = chats.stream().map(this::sessionKey).distinct().count();
        long totalRecherches = chats.stream().filter(this::isSearchIntent).count();
        LocalDateTime lastChat = chats.stream().map(ChatHistory::getDate).max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime lastDemande = demandes.stream().map(Demande::getDateDemande).max(Comparator.naturalOrder()).orElse(null);

        return AssureDashboardDTO.builder()
                .profil(toProfileDTO(assure))
                .totalConversations(totalConversations)
                .totalMessages(chats.size())
                .totalRecherchesStructures(chats.stream().filter(c -> c.getIntention() == ChatIntent.HOSPITAL_SEARCH).count())
                .totalDemandesAssistance(demandes.size())
                .nombreConversations(totalConversations)
                .nombreRecherches(totalRecherches)
                .derniereActivite(Stream.of(lastChat, lastDemande, assure.getUpdatedAt())
                        .filter(value -> value != null)
                        .max(Comparator.naturalOrder())
                        .orElse(null))
                .build();
    }

    @GetMapping("/profile")
    public AssureProfileDTO profile() {
        return toProfileDTO(currentAssure());
    }

    @PutMapping("/profile")
    public AssureProfileDTO updateProfile(@Valid @RequestBody AssureProfileUpdateDTO request) {
        AssureAMU assure = currentAssure();
        assureRepository.findByEmail(request.getEmail())
                .filter(existing -> !existing.getIdAssure().equals(assure.getIdAssure()))
                .ifPresent(existing -> {
                    throw new ConflictException("Cet email est deja utilise");
                });

        assure.setNom(request.getNom());
        assure.setPrenom(request.getPrenom());
        assure.setTelephone(request.getTelephone());
        assure.setEmail(request.getEmail());
        assure.setAdresse(request.getAdresse());
        return toProfileDTO(assureRepository.save(assure));
    }

    @PutMapping("/profile/password")
    public ResponseEntity<Map<String, Object>> updatePassword(@Valid @RequestBody PasswordChangeRequestDTO request) {
        AssureAMU assure = currentAssure();
        if (!passwordEncoder.matches(request.getAncienMotDePasse(), assure.getMotDePasse())) {
            throw new BadRequestException("Ancien mot de passe incorrect");
        }
        if (!request.getNouveauMotDePasse().equals(request.getConfirmationMotDePasse())) {
            throw new BadRequestException("Le nouveau mot de passe et sa confirmation ne correspondent pas");
        }

        assure.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        assureRepository.save(assure);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifie"));
    }

    @GetMapping("/carte")
    public CarteAmuDTO carte() {
        AssureAMU assure = currentAssure();
        return CarteAmuDTO.builder()
                .numeroAmu(assure.getNumeroAMU())
                .titulaire((safe(assure.getPrenom()) + " " + safe(assure.getNom())).trim())
                .statut(assure.getStatut() == null ? null : assure.getStatut().name())
                .dateEmission(assure.getCreatedAt())
                .dateExpiration(null)
                .build();
    }

    @GetMapping("/prestations")
    public PageResponseDTO<PrestationDTO> prestations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CategorieActe categorie,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Pageable pageable = pageable(page, size, mapPrestationSort(sortBy), sortDirection);
        Page<PrestationDTO> result = prestationRepository.searchPrestations(search, true, categorie, pageable)
                .map(this::toPrestationDTO);
        return PageResponseDTO.from(result);
    }

    @GetMapping("/prestations/{id}")
    public PrestationDTO prestation(@PathVariable Long id) {
        Prestation prestation = prestationRepository.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getPrisEnCharge()))
                .orElseThrow(() -> new ResourceNotFoundException("Prestation active introuvable : " + id));
        return toPrestationDTO(prestation);
    }

    @GetMapping("/prestations/search")
    public PageResponseDTO<PrestationDTO> searchPrestations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "query") String query,
            @RequestParam(required = false) CategorieActe categorie,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return prestations(page, size, firstText(search, query), categorie, sortBy, sortDirection);
    }

    @GetMapping("/hospitalisations")
    public PageResponseDTO<HospitalisationTarifDTO> hospitalisations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String typePrestataire,
            @RequestParam(required = false) String chambre,
            @RequestParam(required = false) String population,
            @RequestParam(required = false) String categorie,
            @RequestParam(defaultValue = "typePrestataire") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Pageable pageable = pageable(page, size, mapHospitalisationSort(sortBy), sortDirection);
        return PageResponseDTO.from(hospitalisationTarifService.search(categorie, chambre, population, typePrestataire, pageable));
    }

    @GetMapping("/medicaments")
    public PageResponseDTO<MedicationDTO> medicaments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) Boolean prisEnCharge,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Pageable pageable = pageable(page, size, mapMedicamentSort(sortBy), sortDirection);
        Page<MedicationDTO> result = medicamentRepository.searchOfficialMedicaments(search, prisEnCharge, categorie, true, pageable)
                .map(this::toMedicationDTO);
        return PageResponseDTO.from(result);
    }

    @GetMapping("/medicaments/search")
    public PageResponseDTO<MedicationDTO> searchMedicaments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "query") String query,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) Boolean prisEnCharge,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return medicaments(page, size, firstText(search, query), categorie, prisEnCharge, sortBy, sortDirection);
    }

    @GetMapping("/medicaments/{id}")
    public MedicationDTO medicament(@PathVariable Long id) {
        Medicament medicament = medicamentRepository.findById(id)
                .filter(m -> Boolean.TRUE.equals(m.getActif()))
                .filter(this::isOfficialMedicament)
                .orElseThrow(() -> new ResourceNotFoundException("Medicament actif introuvable : " + id));
        return toMedicationDTO(medicament);
    }

    @GetMapping("/structures")
    public PageResponseDTO<StructureSanteDTO> structures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) TypeStructure type,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        if (type == TypeStructure.PHARMACIE) {
            throw new BadRequestException("Les pharmacies doivent etre consultees via /api/assure/pharmacies");
        }
        Pageable pageable = pageable(page, size, mapStructureSort(sortBy), sortDirection);
        Page<StructureSanteDTO> result = structureRepository.searchStructures(search, ville, type, true, true, pageable)
                .map(s -> toStructureDTO(s, null));
        return PageResponseDTO.from(result);
    }

    @GetMapping("/structures/{id}")
    public StructureSanteDTO structure(@PathVariable Long id) {
        StructureSante structure = structureRepository.findById(id)
                .filter(this::isAccessibleStructure)
                .filter(this::isOfficialStructure)
                .orElseThrow(() -> new ResourceNotFoundException("Structure de sante agreee introuvable : " + id));
        return toStructureDTO(structure, null);
    }

    @GetMapping("/structures/search")
    public PageResponseDTO<StructureSanteDTO> searchStructures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "query") String query,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) TypeStructure type,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return structures(page, size, firstText(search, query), ville, type, sortBy, sortDirection);
    }

    @GetMapping("/structures/nearby")
    public PageResponseDTO<StructureSanteDTO> nearbyStructures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10") double radius) {
        if (radius < 0) {
            throw new BadRequestException("Le rayon doit etre positif");
        }

        List<StructureSanteDTO> nearby = structureRepository.findOfficialHealthStructures().stream()
                .filter(this::isAccessibleStructure)
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .map(s -> toStructureDTO(s, distanceKm(latitude, longitude, s.getLatitude(), s.getLongitude())))
                .filter(s -> s.getDistanceKm() != null && s.getDistanceKm() <= radius)
                .sorted(Comparator.comparing(StructureSanteDTO::getDistanceKm))
                .toList();
        return pageFromList(nearby, page, size);
    }

    @GetMapping("/chatbot/conversations")
    public PageResponseDTO<ConversationSummaryDTO> conversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        AssureAMU assure = currentAssure();
        List<ConversationSummaryDTO> summaries = conversationGroups(assure).values().stream()
                .map(this::toConversationSummary)
                .toList();
        return pageFromList(summaries, page, size);
    }

    @PostMapping("/chatbot/conversations")
    public ResponseEntity<ConversationStartDTO> startConversation() {
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationStartDTO.builder()
                .id(null)
                .sessionId("session-" + UUID.randomUUID())
                .message("Conversation initialisee. Elle sera enregistree au premier message envoye au chatbot.")
                .build());
    }

    @GetMapping("/chatbot/conversations/{id}")
    public ConversationDetailDTO conversation(@PathVariable Long id) {
        AssureAMU assure = currentAssure();
        ChatHistory anchor = chatHistoryRepository.findByIdAndUser_IdAssure(id, assure.getIdAssure())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation introuvable : " + id));
        List<ChatHistory> messages = conversationMessages(assure, anchor);
        return toConversationDetail(messages);
    }

    @DeleteMapping("/chatbot/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id) {
        AssureAMU assure = currentAssure();
        ChatHistory anchor = chatHistoryRepository.findByIdAndUser_IdAssure(id, assure.getIdAssure())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation introuvable : " + id));
        chatHistoryRepository.deleteAll(conversationMessages(assure, anchor));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/demandes-assistance")
    public PageResponseDTO<DemandeResponseDTO> demandesAssistance(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        AssureAMU assure = currentAssure();
        List<DemandeResponseDTO> demandes = demandeRepository.findByAssure_IdAssure(assure.getIdAssure()).stream()
                .sorted(Comparator.comparing(Demande::getDateDemande, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toDemandeDTO)
                .toList();
        return pageFromList(demandes, page, size);
    }

    @GetMapping("/demandes-assistance/{id}")
    public DemandeResponseDTO demandeAssistance(@PathVariable Long id) {
        AssureAMU assure = currentAssure();
        return demandeRepository.findByIdDemandeAndAssure_IdAssure(id, assure.getIdAssure())
                .map(this::toDemandeDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'assistance introuvable : " + id));
    }

    @PostMapping("/demandes-assistance")
    public ResponseEntity<DemandeResponseDTO> createDemandeAssistance(@RequestBody DemandeRequestDTO request) {
        AssureAMU assure = currentAssure();
        if (request.getTypeDemande() == null) {
            throw new BadRequestException("Le type de demande est obligatoire");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BadRequestException("La description est obligatoire");
        }

        Prestation prestation = null;
        if (request.getPrestationId() != null) {
            prestation = prestationRepository.findById(request.getPrestationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable : " + request.getPrestationId()));
        }

        Demande demande = Demande.builder()
                .assure(assure)
                .prestation(prestation)
                .typeDemande(request.getTypeDemande())
                .description(request.getDescription())
                .statut(StatutDemande.EN_ATTENTE)
                .dateDemande(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(toDemandeDTO(demandeRepository.save(demande)));
    }

    @DeleteMapping("/demandes-assistance/{id}")
    public ResponseEntity<Void> deleteDemandeAssistance(@PathVariable Long id) {
        AssureAMU assure = currentAssure();
        Demande demande = demandeRepository.findByIdDemandeAndAssure_IdAssure(id, assure.getIdAssure())
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'assistance introuvable : " + id));
        demandeRepository.delete(demande);
        return ResponseEntity.noContent().build();
    }

    private AssureAMU currentAssure() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.getName().startsWith("ASSURE:")) {
            throw new AccessDeniedException("Acces reserve aux assures");
        }
        String numeroAmu = authentication.getName().substring(7);
        AssureAMU assure = assureRepository.findByNumeroAMU(numeroAmu)
                .orElseThrow(() -> new ResourceNotFoundException("Assure connecte introuvable"));
        if (assure.getStatut() != StatutAssure.ACTIF) {
            throw new AccessDeniedException("Compte assure inactif");
        }
        return assure;
    }

    private Pageable pageable(int page, int size, String sortBy, String sortDirection) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(direction, sortBy));
    }

    private <T> PageResponseDTO<T> pageFromList(List<T> values, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        int from = Math.min(normalizedPage * normalizedSize, values.size());
        int to = Math.min(from + normalizedSize, values.size());
        Page<T> result = new PageImpl<>(values.subList(from, to), PageRequest.of(normalizedPage, normalizedSize), values.size());
        return PageResponseDTO.from(result);
    }

    private boolean isSearchIntent(ChatHistory chat) {
        return chat.getIntention() == ChatIntent.COVERAGE
                || chat.getIntention() == ChatIntent.MEDICATION_SEARCH
                || chat.getIntention() == ChatIntent.HOSPITAL_SEARCH
                || chat.getIntention() == ChatIntent.PROCEDURE;
    }

    private boolean isAccessibleStructure(StructureSante structure) {
        return Boolean.TRUE.equals(structure.getAgrementAMU())
                && structure.getType() != TypeStructure.PHARMACIE
                && (structure.getActif() == null || Boolean.TRUE.equals(structure.getActif()));
    }

    private boolean isOfficialStructure(StructureSante structure) {
        return structure.getCode() != null && !structure.getCode().isBlank();
    }

    private AssureProfileDTO toProfileDTO(AssureAMU a) {
        return AssureProfileDTO.builder()
                .idAssure(a.getIdAssure())
                .nom(a.getNom())
                .prenom(a.getPrenom())
                .numeroAMU(a.getNumeroAMU())
                .dateNaissance(a.getDateNaissance())
                .telephone(a.getTelephone())
                .email(a.getEmail())
                .adresse(a.getAdresse())
                .statut(a.getStatut())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private PrestationDTO toPrestationDTO(Prestation p) {
        return PrestationDTO.builder()
                .idPrestation(p.getIdPrestation())
                .codeActe(p.getCodeActe())
                .nomActe(p.getNomActe())
                .categorie(p.getCategorie() == null ? null : p.getCategorie().name())
                .description(p.getDescription())
                .prisEnCharge(p.getPrisEnCharge())
                .tauxCouverture(p.getTauxCouverture())
                .conditionsPriseEnCharge(p.getConditionsPriseEnCharge())
                .documentsRequis(p.getDocumentsRequis())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private MedicationDTO toMedicationDTO(Medicament m) {
        return MedicationDTO.builder()
                .id(m.getId())
                .code(m.getCode())
                .nom(m.getNom())
                .dci(m.getDci())
                .dosage(m.getDosage())
                .formePharmaceutique(m.getFormePharmaceutique())
                .categorie(m.getCategorie())
                .typeMedicament(m.getTypeMedicament())
                .groupeTherapeutique(m.getGroupeTherapeutique())
                .prixPublic(m.getPrixPublic())
                .baseRemboursement(m.getBaseRemboursement())
                .partInam(m.getPartInam())
                .partBeneficiaire(m.getPartBeneficiaire())
                .prisEnCharge(m.getPrisEnCharge())
                .tauxCouverture(m.getTauxCouverture())
                .conditions(m.getConditions())
                .actif(m.getActif())
                .statut(m.getStatut())
                .source("DATABASE")
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private boolean isOfficialMedicament(Medicament medicament) {
        return medicament.getStatut() != null
                && medicament.getTypeMedicament() != null
                && medicament.getBaseRemboursement() != null;
    }

    private StructureSanteDTO toStructureDTO(StructureSante s, Double distanceKm) {
        return StructureSanteDTO.builder()
                .idStructure(s.getIdStructure())
                .nom(s.getNom())
                .type(s.getType() == null ? null : s.getType().name())
                .adresse(s.getAdresse())
                .ville(s.getVille())
                .region(s.getRegion())
                .telephone(s.getTelephone())
                .email(s.getEmail())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .agrementAMU(s.getAgrementAMU())
                .actif(s.getActif())
                .specialites(s.getSpecialites())
                .horaires(s.getHoraires())
                .distanceKm(distanceKm == null ? null : Math.round(distanceKm * 100.0) / 100.0)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private DemandeResponseDTO toDemandeDTO(Demande demande) {
        return DemandeResponseDTO.builder()
                .idDemande(demande.getIdDemande())
                .typeDemande(demande.getTypeDemande())
                .dateDemande(demande.getDateDemande())
                .statut(demande.getStatut())
                .description(demande.getDescription())
                .resultat(demande.getResultat())
                .build();
    }

    private Map<String, List<ChatHistory>> conversationGroups(AssureAMU assure) {
        Map<String, List<ChatHistory>> groups = new LinkedHashMap<>();
        for (ChatHistory chat : chatHistoryRepository.findByUser_IdAssureOrderByDateDesc(assure.getIdAssure())) {
            groups.computeIfAbsent(sessionKey(chat), key -> new ArrayList<>()).add(chat);
        }
        return groups;
    }

    private List<ChatHistory> conversationMessages(AssureAMU assure, ChatHistory anchor) {
        String key = sessionKey(anchor);
        return conversationGroups(assure).getOrDefault(key, List.of()).stream()
                .sorted(Comparator.comparing(ChatHistory::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private ConversationSummaryDTO toConversationSummary(List<ChatHistory> messages) {
        ChatHistory latest = messages.stream()
                .max(Comparator.comparing(ChatHistory::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
        LocalDateTime firstDate = messages.stream().map(ChatHistory::getDate).min(Comparator.naturalOrder()).orElse(null);
        return ConversationSummaryDTO.builder()
                .id(latest.getId())
                .sessionId(latest.getSessionId())
                .titre(titleFrom(latest.getMessage()))
                .dateCreation(firstDate)
                .dateDernierMessage(latest.getDate())
                .nombreMessages(messages.size())
                .build();
    }

    private ConversationDetailDTO toConversationDetail(List<ChatHistory> messages) {
        ConversationSummaryDTO summary = toConversationSummary(messages);
        return ConversationDetailDTO.builder()
                .id(summary.getId())
                .sessionId(summary.getSessionId())
                .titre(summary.getTitre())
                .dateCreation(summary.getDateCreation())
                .dateDernierMessage(summary.getDateDernierMessage())
                .nombreMessages(summary.getNombreMessages())
                .messages(messages.stream().map(this::toChatMessageDTO).toList())
                .build();
    }

    private ChatMessageDTO toChatMessageDTO(ChatHistory chat) {
        return ChatMessageDTO.builder()
                .id(chat.getId())
                .message(chat.getMessage())
                .answer(chat.getReponse())
                .sessionId(chat.getSessionId())
                .intention(chat.getIntention())
                .date(chat.getDate())
                .build();
    }

    private String sessionKey(ChatHistory chat) {
        return chat.getSessionId() == null || chat.getSessionId().isBlank()
                ? "message-" + chat.getId()
                : chat.getSessionId();
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "Conversation AMU";
        }
        String title = message.trim();
        return title.length() <= 60 ? title : title.substring(0, 57) + "...";
    }

    private String firstText(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String mapPrestationSort(String sortBy) {
        return switch (sortBy == null ? "" : sortBy) {
            case "nom" -> "nomActe";
            case "code" -> "codeActe";
            case "categorie", "tauxCouverture", "createdAt", "updatedAt" -> sortBy;
            default -> "nomActe";
        };
    }

    private String mapMedicamentSort(String sortBy) {
        return switch (sortBy == null ? "" : sortBy) {
            case "code", "categorie", "dci", "createdAt", "updatedAt" -> sortBy;
            default -> "nom";
        };
    }

    private String mapHospitalisationSort(String sortBy) {
        return switch (sortBy == null ? "" : sortBy) {
            case "categorie", "chambre", "population", "dateDebut", "tauxRemboursement", "createdAt", "updatedAt" -> sortBy;
            default -> "typePrestataire";
        };
    }

    private String mapStructureSort(String sortBy) {
        return switch (sortBy == null ? "" : sortBy) {
            case "ville", "region", "type", "createdAt", "updatedAt" -> sortBy;
            default -> "nom";
        };
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusKm = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
