package com.amuguide.backend.controller;

import com.amuguide.backend.dto.*;
import com.amuguide.backend.entity.Administrateur;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.entity.Demande;
import com.amuguide.backend.entity.Historique;
import com.amuguide.backend.enums.StatutAssure;
import com.amuguide.backend.enums.StatutDemande;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.exception.ResourceNotFoundException;
import com.amuguide.backend.repository.AdministrateurRepository;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.DemandeRepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import com.amuguide.backend.service.ContactService;
import com.amuguide.backend.service.DemandeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "CRUD complet - acces reserve a l'administrateur")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AssureAMURepository assureAMURepository;
    private final AdministrateurRepository administrateurRepository;
    private final DemandeRepository demandeRepository;
    private final PrestationRepository prestationRepository;
    private final StructureSanteRepository structureSanteRepository;
    private final DemandeService demandeService;
    private final ContactService contactService;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter DDMMyyyyFmt = DateTimeFormatter.ofPattern("ddMMyyyy");

    @GetMapping("/assures")
    @Operation(summary = "Lister et rechercher les assures")
    public ResponseEntity<?> getAllAssures(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StatutAssure status,
            @RequestParam(defaultValue = "idAssure") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        if (page == null && size == null && (search == null || search.isBlank()) && status == null) {
            List<AssureProfileDTO> assures = assureAMURepository.findAll().stream()
                    .map(this::toProfileDTO)
                    .toList();
            return ResponseEntity.ok(assures);
        }

        int resolvedPage = page != null ? page : 0;
        int resolvedSize = size != null ? size : 10;
        Page<AssureProfileDTO> assures = assureAMURepository
                .searchAssures(search, status, PageRequest.of(resolvedPage, resolvedSize, sort(sortBy, sortDirection)))
                .map(this::toProfileDTO);
        return ResponseEntity.ok(PageResponseDTO.from(assures));
    }

    @GetMapping("/assures/{id}")
    @Operation(summary = "Consulter un assure par ID")
    public ResponseEntity<AssureProfileDTO> getAssureById(@PathVariable Long id) {
        return ResponseEntity.ok(toProfileDTO(findAssure(id)));
    }

    @PostMapping("/assures")
    @Operation(summary = "Creer un nouvel assure")
    public ResponseEntity<AssureProfileDTO> createAssure(@Valid @RequestBody AssureAdminRequestDTO req) {
        if (assureAMURepository.findByNumeroAMU(req.getNumeroAMU()).isPresent()) {
            throw new BadRequestException("Un assure avec le numero AMU " + req.getNumeroAMU() + " existe deja");
        }
        if (assureAMURepository.findByEmail(req.getEmail()).isPresent()) {
            throw new BadRequestException("Un assure avec l'email " + req.getEmail() + " existe deja");
        }

        String rawPassword = (req.getMotDePasse() != null && !req.getMotDePasse().isBlank())
                ? req.getMotDePasse()
                : req.getDateNaissance().format(DDMMyyyyFmt);

        AssureAMU assure = AssureAMU.builder()
                .nom(req.getNom())
                .prenom(req.getPrenom())
                .numeroAMU(req.getNumeroAMU())
                .dateNaissance(req.getDateNaissance())
                .telephone(req.getTelephone())
                .email(req.getEmail())
                .adresse(req.getAdresse())
                .statut(req.getStatut() != null ? req.getStatut() : StatutAssure.ACTIF)
                .motDePasse(passwordEncoder.encode(rawPassword))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(toProfileDTO(assureAMURepository.save(assure)));
    }

    @PutMapping("/assures/{id}")
    @Operation(summary = "Modifier un assure")
    public ResponseEntity<AssureProfileDTO> updateAssure(
            @PathVariable Long id, @Valid @RequestBody AssureAdminRequestDTO req) {
        AssureAMU assure = findAssure(id);
        assureAMURepository.findByEmail(req.getEmail())
                .filter(existing -> !existing.getIdAssure().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Un assure avec l'email " + req.getEmail() + " existe deja");
                });
        assureAMURepository.findByNumeroAMU(req.getNumeroAMU())
                .filter(existing -> !existing.getIdAssure().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Un assure avec le numero AMU " + req.getNumeroAMU() + " existe deja");
                });

        assure.setNom(req.getNom());
        assure.setPrenom(req.getPrenom());
        assure.setNumeroAMU(req.getNumeroAMU());
        assure.setTelephone(req.getTelephone());
        assure.setEmail(req.getEmail());
        assure.setAdresse(req.getAdresse());
        assure.setDateNaissance(req.getDateNaissance());
        if (req.getStatut() != null) {
            assure.setStatut(req.getStatut());
        }
        if (req.getMotDePasse() != null && !req.getMotDePasse().isBlank()) {
            assure.setMotDePasse(passwordEncoder.encode(req.getMotDePasse()));
        }

        return ResponseEntity.ok(toProfileDTO(assureAMURepository.save(assure)));
    }

    @PatchMapping("/assures/{id}/status")
    @Operation(summary = "Activer ou desactiver un assure")
    public ResponseEntity<AssureProfileDTO> updateAssureStatus(
            @PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO req) {
        AssureAMU assure = findAssure(id);
        assure.setStatut(Boolean.TRUE.equals(req.getActif()) ? StatutAssure.ACTIF : StatutAssure.INACTIF);
        return ResponseEntity.ok(toProfileDTO(assureAMURepository.save(assure)));
    }

    @DeleteMapping("/assures/{id}")
    @Operation(summary = "Supprimer un assure, ou le desactiver s'il a des relations")
    public ResponseEntity<Void> deleteAssure(@PathVariable Long id) {
        AssureAMU assure = findAssure(id);
        if (!demandeRepository.findByAssure_IdAssure(id).isEmpty()) {
            assure.setStatut(StatutAssure.INACTIF);
            assureAMURepository.save(assure);
            return ResponseEntity.noContent().build();
        }
        assureAMURepository.delete(assure);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/administrateurs")
    public ResponseEntity<PageResponseDTO<AdminProfileDTO>> getAllAdmins(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean status,
            @RequestParam(defaultValue = "idAdmin") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Page<AdminProfileDTO> admins = administrateurRepository
                .searchAdministrateurs(search, status, PageRequest.of(page, size, sort(sortBy, sortDirection)))
                .map(this::toAdminDTO);
        return ResponseEntity.ok(PageResponseDTO.from(admins));
    }

    @GetMapping("/administrateurs/{id}")
    public ResponseEntity<AdminProfileDTO> getAdminById(@PathVariable Long id) {
        return ResponseEntity.ok(toAdminDTO(findAdmin(id)));
    }

    @PostMapping("/administrateurs")
    public ResponseEntity<AdminProfileDTO> createAdmin(@Valid @RequestBody AdminRequestDTO req) {
        if (req.getMotDePasse() == null || req.getMotDePasse().isBlank()) {
            throw new BadRequestException("Le mot de passe est obligatoire a la creation");
        }
        if (administrateurRepository.findByLogin(req.getLogin()).isPresent()) {
            throw new BadRequestException("Un administrateur avec le login " + req.getLogin() + " existe deja");
        }
        if (administrateurRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new BadRequestException("Un administrateur avec l'email " + req.getEmail() + " existe deja");
        }

        Administrateur admin = Administrateur.builder()
                .nom(req.getNom())
                .prenom(req.getPrenom())
                .email(req.getEmail())
                .login(req.getLogin())
                .motDePasse(passwordEncoder.encode(req.getMotDePasse()))
                .actif(req.getActif() != null ? req.getActif() : true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(toAdminDTO(administrateurRepository.save(admin)));
    }

    @PutMapping("/administrateurs/{id}")
    public ResponseEntity<AdminProfileDTO> updateAdmin(@PathVariable Long id, @Valid @RequestBody AdminRequestDTO req) {
        Administrateur admin = findAdmin(id);
        administrateurRepository.findByEmail(req.getEmail())
                .filter(existing -> !existing.getIdAdmin().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Un administrateur avec l'email " + req.getEmail() + " existe deja");
                });
        administrateurRepository.findByLogin(req.getLogin())
                .filter(existing -> !existing.getIdAdmin().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Un administrateur avec le login " + req.getLogin() + " existe deja");
                });
        admin.setNom(req.getNom());
        admin.setPrenom(req.getPrenom());
        admin.setEmail(req.getEmail());
        admin.setLogin(req.getLogin());
        if (req.getActif() != null) {
            admin.setActif(req.getActif());
        }
        if (req.getMotDePasse() != null && !req.getMotDePasse().isBlank()) {
            admin.setMotDePasse(passwordEncoder.encode(req.getMotDePasse()));
        }
        return ResponseEntity.ok(toAdminDTO(administrateurRepository.save(admin)));
    }

    @PatchMapping("/administrateurs/{id}/status")
    public ResponseEntity<AdminProfileDTO> updateAdminStatus(
            @PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO req) {
        Administrateur admin = findAdmin(id);
        if (!Boolean.TRUE.equals(req.getActif()) && Boolean.TRUE.equals(admin.getActif())
                && administrateurRepository.countByActifTrue() <= 1) {
            throw new BadRequestException("Impossible de desactiver le dernier administrateur actif");
        }
        admin.setActif(req.getActif());
        return ResponseEntity.ok(toAdminDTO(administrateurRepository.save(admin)));
    }

    @DeleteMapping("/administrateurs/{id}")
    public ResponseEntity<Void> deleteAdmin(@PathVariable Long id, Authentication authentication) {
        Administrateur admin = findAdmin(id);
        String currentLogin = currentAdminLogin(authentication);
        if (admin.getLogin().equals(currentLogin)) {
            throw new BadRequestException("Un administrateur ne peut pas supprimer son propre compte");
        }
        if (Boolean.TRUE.equals(admin.getActif()) && administrateurRepository.countByActifTrue() <= 1) {
            throw new BadRequestException("Impossible de supprimer le dernier administrateur actif");
        }
        administrateurRepository.delete(admin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/demandes")
    public ResponseEntity<List<Demande>> getAllDemandes() {
        return ResponseEntity.ok(demandeService.getAllDemandes());
    }

    @GetMapping("/demandes/{id}")
    public ResponseEntity<Demande> getDemandeById(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.getDemandeById(id));
    }

    @GetMapping("/demandes/{id}/historiques")
    public ResponseEntity<List<Historique>> getHistoriques(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.getHistoriquesByDemande(id));
    }

    @PutMapping("/demandes/{id}/resultat")
    public ResponseEntity<DemandeResponseDTO> updateResultat(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String resultat = body.get("resultat");
        StatutDemande statut = StatutDemande.valueOf(body.get("statut"));
        return ResponseEntity.ok(toDemandeDTO(demandeService.updateResultatDemande(id, resultat, statut)));
    }

    @DeleteMapping("/demandes/{id}")
    public ResponseEntity<Void> deleteDemande(@PathVariable Long id) {
        demandeService.supprimerDemande(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/contact")
    public ResponseEntity<List<ContactResponseDTO>> getAllContactMessages() {
        return ResponseEntity.ok(contactService.getAllMessages());
    }

    @PutMapping("/contact/{id}/traite")
    public ResponseEntity<ContactResponseDTO> marquerTraite(@PathVariable Long id) {
        return ResponseEntity.ok(contactService.marquerCommeTraite(id));
    }

    @PutMapping("/contact/{id}/reponse")
    public ResponseEntity<ContactResponseDTO> repondreContact(
            @PathVariable Long id, @Valid @RequestBody ContactReponseRequestDTO req) {
        return ResponseEntity.ok(contactService.repondre(id, req.getReponse()));
    }

    @DeleteMapping("/contact/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        contactService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<Demande> demandes = demandeService.getAllDemandes();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAssures", assureAMURepository.count());
        stats.put("totalAdmins", administrateurRepository.count());
        stats.put("totalAdministrateurs", administrateurRepository.count());
        stats.put("totalDemandes", demandes.size());
        stats.put("demandesEnAttente", demandes.stream().filter(d -> d.getStatut() == StatutDemande.EN_ATTENTE).count());
        stats.put("demandesTraitees", demandes.stream().filter(d -> d.getStatut() == StatutDemande.TRAITEE).count());
        stats.put("demandesRejetees", demandes.stream().filter(d -> d.getStatut() == StatutDemande.REJETEE).count());
        stats.put("totalStructures", structureSanteRepository.count());
        stats.put("totalPrestations", prestationRepository.count());
        stats.put("totalMessages", contactService.getAllMessages().size());
        stats.put("totalMessagesContact", contactService.getAllMessages().size());
        return ResponseEntity.ok(stats);
    }

    private AssureAMU findAssure(Long id) {
        return assureAMURepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assure non trouve : " + id));
    }

    private Administrateur findAdmin(Long id) {
        return administrateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur non trouve : " + id));
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

    private AdminProfileDTO toAdminDTO(Administrateur a) {
        return AdminProfileDTO.builder()
                .idAdmin(a.getIdAdmin())
                .nom(a.getNom())
                .prenom(a.getPrenom())
                .email(a.getEmail())
                .login(a.getLogin())
                .actif(a.getActif())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private Sort sort(String sortBy, String sortDirection) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, sortBy);
    }

    private String currentAdminLogin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || !authentication.getName().startsWith("ADMIN:")) {
            return null;
        }
        return authentication.getName().substring(6);
    }

    private DemandeResponseDTO toDemandeDTO(Demande d) {
        return DemandeResponseDTO.builder()
                .idDemande(d.getIdDemande())
                .typeDemande(d.getTypeDemande())
                .dateDemande(d.getDateDemande())
                .statut(d.getStatut())
                .description(d.getDescription())
                .resultat(d.getResultat())
                .build();
    }
}
