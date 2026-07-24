package com.amuguide.backend.controller;

import com.amuguide.backend.dto.*;
import com.amuguide.backend.entity.ChatHistory;
import com.amuguide.backend.entity.FaqChatbot;
import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.enums.FaqChatbotCategorie;
import com.amuguide.backend.enums.StatutAssure;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.exception.ResourceNotFoundException;
import com.amuguide.backend.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AssureAMURepository assureRepository;
    private final AdministrateurRepository administrateurRepository;
    private final PrestationRepository prestationRepository;
    private final StructureSanteRepository structureRepository;
    private final FaqChatbotRepository faqRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final MedicamentRepository medicamentRepository;

    @GetMapping("/dashboard/statistics")
    public AdminDashboardStatisticsDTO statistics() {
        return AdminDashboardStatisticsDTO.builder()
                .totalAssures(assureRepository.count())
                .assuresActifs(assureRepository.countByStatut(StatutAssure.ACTIF))
                .assuresInactifs(assureRepository.countByStatut(StatutAssure.INACTIF))
                .totalAdministrateurs(administrateurRepository.count())
                .administrateursActifs(administrateurRepository.countByActifTrue())
                .totalPrestations(prestationRepository.count())
                .prestationsActives(prestationRepository.countByPrisEnChargeTrue())
                .totalMedicaments(medicamentRepository.count())
                .medicamentsPrisEnCharge(medicamentRepository.countByPrisEnChargeTrue())
                .totalStructures(structureRepository.count())
                .structuresActives(structureRepository.countActiveStructures())
                .totalFaq(faqRepository.count())
                .faqActives(faqRepository.countByActifTrue())
                .totalQuestionsChatbot(faqRepository.count())
                .totalConversations(chatHistoryRepository.count())
                .build();
    }

    @GetMapping("/dashboard/recent-activities")
    public List<RecentActivityDTO> recentActivities(@RequestParam(defaultValue = "10") int limit) {
        List<RecentActivityDTO> activities = new ArrayList<>();
        assureRepository.findAll().stream()
                .filter(a -> a.getCreatedAt() != null)
                .map(a -> activity("NOUVEL_ASSURE", a.getNom() + " " + a.getPrenom(), a.getIdAssure(), a.getCreatedAt()))
                .forEach(activities::add);
        administrateurRepository.findAll().stream()
                .filter(a -> a.getCreatedAt() != null)
                .map(a -> activity("NOUVEL_ADMINISTRATEUR", a.getNom() + " " + a.getPrenom(), a.getIdAdmin(), a.getCreatedAt()))
                .forEach(activities::add);
        prestationRepository.findAll().stream()
                .filter(p -> p.getCreatedAt() != null)
                .map(p -> activity("NOUVELLE_PRESTATION", p.getNomActe(), p.getIdPrestation(), p.getCreatedAt()))
                .forEach(activities::add);
        medicamentRepository.findAll().stream()
                .filter(m -> m.getCreatedAt() != null)
                .map(m -> activity("NOUVEAU_MEDICAMENT", m.getNom(), m.getId(), m.getCreatedAt()))
                .forEach(activities::add);
        structureRepository.findAll().stream()
                .filter(s -> s.getCreatedAt() != null)
                .map(s -> activity("NOUVELLE_STRUCTURE", s.getNom(), s.getIdStructure(), s.getCreatedAt()))
                .forEach(activities::add);
        faqRepository.findAll().stream()
                .filter(f -> f.getCreatedAt() != null)
                .map(f -> activity("NOUVELLE_FAQ", f.getQuestion(), f.getId(), f.getCreatedAt()))
                .forEach(activities::add);

        return activities.stream()
                .sorted(Comparator.comparing(RecentActivityDTO::getDate).reversed())
                .limit(limit)
                .toList();
    }

    @GetMapping("/prestations")
    public PageResponseDTO<PrestationDTO> prestations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean status,
            @RequestParam(required = false) CategorieActe categorie,
            @RequestParam(defaultValue = "idPrestation") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Page<PrestationDTO> result = prestationRepository
                .searchPrestations(search, status, categorie, PageRequest.of(page, size, sort(sortBy, sortDirection)))
                .map(this::toPrestationDTO);
        return PageResponseDTO.from(result);
    }

    @GetMapping("/prestations/{id}")
    public PrestationDTO prestation(@PathVariable Long id) {
        return toPrestationDTO(findPrestation(id));
    }

    @PostMapping("/prestations")
    @ResponseStatus(HttpStatus.CREATED)
    public PrestationDTO createPrestation(@Valid @RequestBody PrestationDTO dto) {
        prestationRepository.findByCodeActe(dto.getCodeActe()).ifPresent(existing -> {
            throw new BadRequestException("Une prestation avec ce code existe deja");
        });
        return toPrestationDTO(prestationRepository.save(toPrestation(dto, new Prestation())));
    }

    @PutMapping("/prestations/{id}")
    public PrestationDTO updatePrestation(@PathVariable Long id, @Valid @RequestBody PrestationDTO dto) {
        prestationRepository.findByCodeActe(dto.getCodeActe())
                .filter(existing -> !existing.getIdPrestation().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Une prestation avec ce code existe deja");
                });
        return toPrestationDTO(prestationRepository.save(toPrestation(dto, findPrestation(id))));
    }

    @PatchMapping("/prestations/{id}/status")
    public PrestationDTO updatePrestationStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO req) {
        Prestation prestation = findPrestation(id);
        prestation.setPrisEnCharge(req.getActif());
        return toPrestationDTO(prestationRepository.save(prestation));
    }

    @DeleteMapping("/prestations/{id}")
    public ResponseEntity<Void> deletePrestation(@PathVariable Long id) {
        Prestation prestation = findPrestation(id);
        if (prestation.getStructures() != null && !prestation.getStructures().isEmpty()) {
            prestation.setPrisEnCharge(false);
            prestationRepository.save(prestation);
            return ResponseEntity.noContent().build();
        }
        prestationRepository.delete(prestation);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/structures")
    public PageResponseDTO<StructureSanteDTO> structures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) TypeStructure type,
            @RequestParam(required = false) Boolean status,
            @RequestParam(required = false) Boolean agrement,
            @RequestParam(defaultValue = "idStructure") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Page<StructureSanteDTO> result = structureRepository
                .searchStructures(search, ville, type, status, agrement, PageRequest.of(page, size, sort(sortBy, sortDirection)))
                .map(this::toStructureDTO);
        return PageResponseDTO.from(result);
    }

    @GetMapping("/structures/{id}")
    public StructureSanteDTO structure(@PathVariable Long id) {
        return toStructureDTO(findStructure(id));
    }

    @PostMapping("/structures")
    @ResponseStatus(HttpStatus.CREATED)
    public StructureSanteDTO createStructure(@Valid @RequestBody StructureSanteDTO dto) {
        structureRepository.findByNomAndVille(dto.getNom(), dto.getVille()).ifPresent(existing -> {
            throw new BadRequestException("Une structure avec ce nom existe deja dans cette ville");
        });
        return toStructureDTO(structureRepository.save(toStructure(dto, new StructureSante())));
    }

    @PutMapping("/structures/{id}")
    public StructureSanteDTO updateStructure(@PathVariable Long id, @Valid @RequestBody StructureSanteDTO dto) {
        structureRepository.findByNomAndVille(dto.getNom(), dto.getVille())
                .filter(existing -> !existing.getIdStructure().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Une structure avec ce nom existe deja dans cette ville");
                });
        return toStructureDTO(structureRepository.save(toStructure(dto, findStructure(id))));
    }

    @PatchMapping("/structures/{id}/status")
    public StructureSanteDTO updateStructureStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO req) {
        StructureSante structure = findStructure(id);
        structure.setActif(req.getActif());
        return toStructureDTO(structureRepository.save(structure));
    }

    @DeleteMapping("/structures/{id}")
    public ResponseEntity<Void> deleteStructure(@PathVariable Long id) {
        StructureSante structure = findStructure(id);
        if (structure.getPrestations() != null && !structure.getPrestations().isEmpty()) {
            structure.setAgrementAMU(false);
            structureRepository.save(structure);
            return ResponseEntity.noContent().build();
        }
        structureRepository.delete(structure);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/faqs")
    public PageResponseDTO<FaqChatbotDTO> faqs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) FaqChatbotCategorie categorie,
            @RequestParam(required = false) Boolean status,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Page<FaqChatbotDTO> result = faqRepository
                .searchFaqs(search, categorie, status, PageRequest.of(page, size, sort(sortBy, sortDirection)))
                .map(this::toFaqDTO);
        return PageResponseDTO.from(result);
    }

    @GetMapping("/faqs/{id}")
    public FaqChatbotDTO faq(@PathVariable Long id) {
        return toFaqDTO(findFaq(id));
    }

    @PostMapping("/faqs")
    @ResponseStatus(HttpStatus.CREATED)
    public FaqChatbotDTO createFaq(@Valid @RequestBody FaqChatbotDTO dto) {
        faqRepository.findByCode(dto.getCode()).ifPresent(existing -> {
            throw new BadRequestException("Une FAQ avec ce code existe deja");
        });
        faqRepository.findByQuestionIgnoreCase(dto.getQuestion()).ifPresent(existing -> {
            throw new BadRequestException("Une FAQ avec cette question existe deja");
        });
        return toFaqDTO(faqRepository.save(toFaq(dto, new FaqChatbot())));
    }

    @PutMapping("/faqs/{id}")
    public FaqChatbotDTO updateFaq(@PathVariable Long id, @Valid @RequestBody FaqChatbotDTO dto) {
        faqRepository.findByCode(dto.getCode())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Une FAQ avec ce code existe deja");
                });
        faqRepository.findByQuestionIgnoreCase(dto.getQuestion())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Une FAQ avec cette question existe deja");
                });
        return toFaqDTO(faqRepository.save(toFaq(dto, findFaq(id))));
    }

    @PatchMapping("/faqs/{id}/status")
    public FaqChatbotDTO updateFaqStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO req) {
        FaqChatbot faq = findFaq(id);
        faq.setActif(req.getActif());
        return toFaqDTO(faqRepository.save(faq));
    }

    @DeleteMapping("/faqs/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        faqRepository.delete(findFaq(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chatbot/conversations")
    public Page<Map<String, Object>> conversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return chatHistoryRepository.findAll(PageRequest.of(page, size)).map(this::toConversationMap);
    }

    @GetMapping("/chatbot/conversations/{id}")
    public Map<String, Object> conversation(@PathVariable Long id) {
        return toConversationMap(chatHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation introuvable : " + id)));
    }

    @GetMapping("/chatbot/statistics")
    public Map<String, Object> chatbotStatistics() {
        return Map.of(
                "totalConversations", chatHistoryRepository.count(),
                "totalFaqs", faqRepository.count(),
                "faqsActives", faqRepository.countByActifTrue(),
                "messagesDernieres24h", chatHistoryRepository.countByDateAfter(LocalDateTime.now().minusDays(1)),
                "questionsParIntention", chatHistoryRepository.countByIntent(),
                "messagesParJour", chatHistoryRepository.countByDay()
        );
    }

    @GetMapping("/medicaments")
    public PageResponseDTO<MedicationDTO> medicaments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean prisEnCharge,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) Boolean status,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Page<MedicationDTO> result = medicamentRepository
                .searchMedicaments(search, prisEnCharge, categorie, status, PageRequest.of(page, size, sort(sortBy, sortDirection)))
                .map(this::toMedicationDTO);
        return PageResponseDTO.from(result);
    }

    @GetMapping("/medicaments/search")
    public PageResponseDTO<MedicationDTO> searchMedicaments(@RequestParam String query) {
        Page<MedicationDTO> result = medicamentRepository
                .searchMedicaments(query, null, null, true, PageRequest.of(0, 50, Sort.by("nom")))
                .map(this::toMedicationDTO);
        return PageResponseDTO.from(result);
    }

    @GetMapping("/medicaments/{id}")
    public MedicationDTO medicament(@PathVariable Long id) {
        return toMedicationDTO(findMedicament(id));
    }

    @PostMapping("/medicaments")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationDTO createMedicament(@Valid @RequestBody MedicationDTO dto) {
        medicamentRepository.findByCode(dto.getCode()).ifPresent(existing -> {
            throw new BadRequestException("Un medicament avec ce code existe deja");
        });
        return toMedicationDTO(medicamentRepository.save(toMedicament(dto, new Medicament())));
    }

    @PutMapping("/medicaments/{id}")
    public MedicationDTO updateMedicament(@PathVariable Long id, @Valid @RequestBody MedicationDTO dto) {
        medicamentRepository.findByCode(dto.getCode())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Un medicament avec ce code existe deja");
                });
        return toMedicationDTO(medicamentRepository.save(toMedicament(dto, findMedicament(id))));
    }

    @PatchMapping("/medicaments/{id}/status")
    public MedicationDTO updateMedicamentStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO req) {
        Medicament medicament = findMedicament(id);
        medicament.setActif(req.getActif());
        return toMedicationDTO(medicamentRepository.save(medicament));
    }

    @DeleteMapping("/medicaments/{id}")
    public ResponseEntity<Void> deleteMedicament(@PathVariable Long id) {
        medicamentRepository.delete(findMedicament(id));
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toConversationMap(ChatHistory chat) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", chat.getId());
        body.put("assureId", chat.getUser() == null ? null : chat.getUser().getIdAssure());
        body.put("message", chat.getMessage());
        body.put("answer", chat.getReponse());
        body.put("sessionId", chat.getSessionId());
        body.put("intention", chat.getIntention());
        body.put("date", chat.getDate());
        return body;
    }

    private Prestation findPrestation(Long id) {
        return prestationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable : " + id));
    }

    private StructureSante findStructure(Long id) {
        return structureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Structure introuvable : " + id));
    }

    private FaqChatbot findFaq(Long id) {
        return faqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FAQ introuvable : " + id));
    }

    private Medicament findMedicament(Long id) {
        return medicamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicament introuvable : " + id));
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

    private Prestation toPrestation(PrestationDTO dto, Prestation p) {
        p.setCodeActe(dto.getCodeActe());
        p.setNomActe(dto.getNomActe());
        p.setCategorie(parseCategorie(dto.getCategorie()));
        p.setDescription(dto.getDescription());
        p.setPrisEnCharge(dto.getPrisEnCharge());
        p.setTauxCouverture(dto.getTauxCouverture());
        p.setConditionsPriseEnCharge(dto.getConditionsPriseEnCharge());
        p.setDocumentsRequis(dto.getDocumentsRequis());
        return p;
    }

    private StructureSanteDTO toStructureDTO(StructureSante s) {
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
                .actif(s.getActif() == null ? true : s.getActif())
                .specialites(s.getSpecialites())
                .horaires(s.getHoraires())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private StructureSante toStructure(StructureSanteDTO dto, StructureSante s) {
        s.setNom(dto.getNom());
        s.setType(parseType(dto.getType()));
        s.setAdresse(dto.getAdresse());
        s.setVille(dto.getVille());
        s.setRegion(dto.getRegion());
        s.setTelephone(dto.getTelephone());
        s.setEmail(dto.getEmail());
        s.setLatitude(dto.getLatitude());
        s.setLongitude(dto.getLongitude());
        s.setAgrementAMU(dto.getAgrementAMU());
        s.setActif(dto.getActif() != null ? dto.getActif() : true);
        s.setSpecialites(dto.getSpecialites());
        s.setHoraires(dto.getHoraires());
        return s;
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
                .prisEnCharge(m.getPrisEnCharge())
                .tauxCouverture(m.getTauxCouverture())
                .conditions(m.getConditions())
                .actif(m.getActif())
                .statut(Boolean.TRUE.equals(m.getActif()) ? "ACTIF" : "INACTIF")
                .source("POSTGRESQL")
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private Medicament toMedicament(MedicationDTO dto, Medicament m) {
        m.setCode(dto.getCode());
        m.setNom(dto.getNom());
        m.setDci(dto.getDci());
        m.setDosage(dto.getDosage());
        m.setFormePharmaceutique(dto.getFormePharmaceutique());
        m.setCategorie(dto.getCategorie());
        m.setPrisEnCharge(dto.getPrisEnCharge() != null ? dto.getPrisEnCharge() : true);
        m.setTauxCouverture(dto.getTauxCouverture());
        m.setConditions(dto.getConditions());
        m.setActif(dto.getActif() != null ? dto.getActif() : true);
        return m;
    }

    private FaqChatbotDTO toFaqDTO(FaqChatbot f) {
        return FaqChatbotDTO.builder()
                .id(f.getId())
                .code(f.getCode())
                .question(f.getQuestion())
                .reponse(f.getReponse())
                .categorie(f.getCategorie())
                .motsCles(f.getMotsCles())
                .priorite(f.getPriorite())
                .actif(f.getActif())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    private FaqChatbot toFaq(FaqChatbotDTO dto, FaqChatbot f) {
        f.setCode(dto.getCode());
        f.setQuestion(dto.getQuestion());
        f.setReponse(dto.getReponse());
        f.setCategorie(dto.getCategorie());
        f.setMotsCles(dto.getMotsCles());
        f.setPriorite(dto.getPriorite() != null ? dto.getPriorite() : 0);
        f.setActif(dto.getActif() != null ? dto.getActif() : true);
        return f;
    }

    private CategorieActe parseCategorie(String value) {
        try {
            return CategorieActe.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Categorie de prestation invalide : " + value);
        }
    }

    private TypeStructure parseType(String value) {
        try {
            return TypeStructure.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Type de structure invalide : " + value);
        }
    }

    private RecentActivityDTO activity(String type, String label, Long entityId, LocalDateTime date) {
        return RecentActivityDTO.builder()
                .type(type)
                .label(label)
                .entityId(entityId)
                .date(date)
                .build();
    }

    private Sort sort(String sortBy, String sortDirection) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, sortBy);
    }
}
