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
import com.amuguide.backend.service.ContactService;
import com.amuguide.backend.service.DemandeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "CRUD complet — accès réservé à l'administrateur")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AssureAMURepository assureAMURepository;
    private final AdministrateurRepository administrateurRepository;
    private final DemandeService demandeService;
    private final ContactService contactService;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter DDMMyyyyFmt = DateTimeFormatter.ofPattern("ddMMyyyy");

    // ═══════════════════════════════════════════════════════════════════════
    // ASSURÉS — CRUD complet
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/assures")
    @Operation(summary = "Lister tous les assurés")
    public ResponseEntity<List<AssureProfileDTO>> getAllAssures() {
        return ResponseEntity.ok(assureAMURepository.findAll().stream()
                .map(this::toProfileDTO).toList());
    }

    @GetMapping("/assures/{id}")
    @Operation(summary = "Consulter un assuré par ID")
    public ResponseEntity<AssureProfileDTO> getAssureById(@PathVariable Long id) {
        return ResponseEntity.ok(toProfileDTO(findAssure(id)));
    }

    @PostMapping("/assures")
    @Operation(summary = "Créer un nouvel assuré",
               description = "Si motDePasse absent, le défaut est la dateNaissance au format ddMMyyyy")
    public ResponseEntity<AssureProfileDTO> createAssure(@Valid @RequestBody AssureAdminRequestDTO req) {
        if (assureAMURepository.findByNumeroAMU(req.getNumeroAMU()).isPresent()) {
            throw new BadRequestException("Un assuré avec le numéro AMU " + req.getNumeroAMU() + " existe déjà");
        }
        if (assureAMURepository.findByEmail(req.getEmail()).isPresent()) {
            throw new BadRequestException("Un assuré avec l'email " + req.getEmail() + " existe déjà");
        }

        String rawPassword = (req.getMotDePasse() != null && !req.getMotDePasse().isBlank())
                ? req.getMotDePasse()
                : req.getDateNaissance().format(DDMMyyyyFmt);

        AssureAMU assure = AssureAMU.builder()
                .nom(req.getNom()).prenom(req.getPrenom()).numeroAMU(req.getNumeroAMU())
                .dateNaissance(req.getDateNaissance()).telephone(req.getTelephone())
                .email(req.getEmail()).adresse(req.getAdresse())
                .statut(req.getStatut() != null ? req.getStatut() : StatutAssure.ACTIF)
                .motDePasse(passwordEncoder.encode(rawPassword))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(toProfileDTO(assureAMURepository.save(assure)));
    }

    @PutMapping("/assures/{id}")
    @Operation(summary = "Modifier un assuré",
               description = "motDePasse absent = inchangé")
    public ResponseEntity<AssureProfileDTO> updateAssure(
            @PathVariable Long id, @Valid @RequestBody AssureAdminRequestDTO req) {

        AssureAMU assure = findAssure(id);

        assure.setNom(req.getNom());
        assure.setPrenom(req.getPrenom());
        assure.setTelephone(req.getTelephone());
        assure.setEmail(req.getEmail());
        assure.setAdresse(req.getAdresse());
        assure.setDateNaissance(req.getDateNaissance());
        if (req.getStatut() != null) assure.setStatut(req.getStatut());
        if (req.getMotDePasse() != null && !req.getMotDePasse().isBlank()) {
            assure.setMotDePasse(passwordEncoder.encode(req.getMotDePasse()));
        }

        return ResponseEntity.ok(toProfileDTO(assureAMURepository.save(assure)));
    }

    @DeleteMapping("/assures/{id}")
    @Operation(summary = "Supprimer un assuré (et toutes ses demandes)")
    public ResponseEntity<Void> deleteAssure(@PathVariable Long id) {
        assureAMURepository.delete(findAssure(id));
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMINISTRATEURS — CRUD complet
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/administrateurs")
    @Operation(summary = "Lister tous les administrateurs")
    public ResponseEntity<List<AdminProfileDTO>> getAllAdmins() {
        return ResponseEntity.ok(administrateurRepository.findAll().stream()
                .map(this::toAdminDTO).toList());
    }

    @GetMapping("/administrateurs/{id}")
    @Operation(summary = "Consulter un administrateur par ID")
    public ResponseEntity<AdminProfileDTO> getAdminById(@PathVariable Long id) {
        return ResponseEntity.ok(toAdminDTO(findAdmin(id)));
    }

    @PostMapping("/administrateurs")
    @Operation(summary = "Créer un nouvel administrateur")
    public ResponseEntity<AdminProfileDTO> createAdmin(@Valid @RequestBody AdminRequestDTO req) {
        if (req.getMotDePasse() == null || req.getMotDePasse().isBlank()) {
            throw new BadRequestException("Le mot de passe est obligatoire à la création");
        }
        if (administrateurRepository.findByLogin(req.getLogin()).isPresent()) {
            throw new BadRequestException("Un administrateur avec le login " + req.getLogin() + " existe déjà");
        }
        if (administrateurRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new BadRequestException("Un administrateur avec l'email " + req.getEmail() + " existe déjà");
        }

        Administrateur admin = Administrateur.builder()
                .nom(req.getNom()).prenom(req.getPrenom()).email(req.getEmail())
                .login(req.getLogin()).motDePasse(passwordEncoder.encode(req.getMotDePasse()))
                .actif(req.getActif() != null ? req.getActif() : true)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(toAdminDTO(administrateurRepository.save(admin)));
    }

    @PutMapping("/administrateurs/{id}")
    @Operation(summary = "Modifier un administrateur",
               description = "motDePasse absent = inchangé")
    public ResponseEntity<AdminProfileDTO> updateAdmin(
            @PathVariable Long id, @Valid @RequestBody AdminRequestDTO req) {

        Administrateur admin = findAdmin(id);

        admin.setNom(req.getNom());
        admin.setPrenom(req.getPrenom());
        admin.setEmail(req.getEmail());
        admin.setLogin(req.getLogin());
        if (req.getActif() != null) admin.setActif(req.getActif());
        if (req.getMotDePasse() != null && !req.getMotDePasse().isBlank()) {
            admin.setMotDePasse(passwordEncoder.encode(req.getMotDePasse()));
        }

        return ResponseEntity.ok(toAdminDTO(administrateurRepository.save(admin)));
    }

    @DeleteMapping("/administrateurs/{id}")
    @Operation(summary = "Supprimer un administrateur")
    public ResponseEntity<Void> deleteAdmin(@PathVariable Long id) {
        administrateurRepository.delete(findAdmin(id));
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DEMANDES — lecture + mise à jour résultat + suppression
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/demandes")
    @Operation(summary = "Lister toutes les demandes")
    public ResponseEntity<List<Demande>> getAllDemandes() {
        return ResponseEntity.ok(demandeService.getAllDemandes());
    }

    @GetMapping("/demandes/{id}")
    @Operation(summary = "Consulter une demande")
    public ResponseEntity<Demande> getDemandeById(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.getDemandeById(id));
    }

    @GetMapping("/demandes/{id}/historiques")
    @Operation(summary = "Historique d'une demande")
    public ResponseEntity<List<Historique>> getHistoriques(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.getHistoriquesByDemande(id));
    }

    @PutMapping("/demandes/{id}/resultat")
    @Operation(summary = "Mettre à jour le résultat d'une demande")
    public ResponseEntity<DemandeResponseDTO> updateResultat(
            @PathVariable Long id, @RequestBody Map<String, String> body) {

        String resultat = body.get("resultat");
        StatutDemande statut = StatutDemande.valueOf(body.get("statut"));
        Demande updated = demandeService.updateResultatDemande(id, resultat, statut);
        return ResponseEntity.ok(toDemandeDTO(updated));
    }

    @DeleteMapping("/demandes/{id}")
    @Operation(summary = "Supprimer une demande et son historique")
    public ResponseEntity<Void> deleteDemande(@PathVariable Long id) {
        demandeService.supprimerDemande(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MESSAGES DE CONTACT — lecture + réponse + suppression
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/contact")
    @Operation(summary = "Lister tous les messages de contact")
    public ResponseEntity<List<ContactResponseDTO>> getAllContactMessages() {
        return ResponseEntity.ok(contactService.getAllMessages());
    }

    @PutMapping("/contact/{id}/traite")
    @Operation(summary = "Marquer un message comme traité")
    public ResponseEntity<ContactResponseDTO> marquerTraite(@PathVariable Long id) {
        return ResponseEntity.ok(contactService.marquerCommeTraite(id));
    }

    @PutMapping("/contact/{id}/reponse")
    @Operation(summary = "Répondre à un message de contact",
               description = "Enregistre la réponse et marque automatiquement le message comme traité")
    public ResponseEntity<ContactResponseDTO> repondreContact(
            @PathVariable Long id, @Valid @RequestBody ContactReponseRequestDTO req) {
        return ResponseEntity.ok(contactService.repondre(id, req.getReponse()));
    }

    @DeleteMapping("/contact/{id}")
    @Operation(summary = "Supprimer un message de contact")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        contactService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATISTIQUES
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales du système")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalAssures",        assureAMURepository.count(),
                "totalAdmins",         administrateurRepository.count(),
                "totalDemandes",       demandeService.getAllDemandes().size(),
                "totalMessagesContact", contactService.getAllMessages().size()
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers privés
    // ═══════════════════════════════════════════════════════════════════════

    private AssureAMU findAssure(Long id) {
        return assureAMURepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assuré non trouvé : " + id));
    }

    private Administrateur findAdmin(Long id) {
        return administrateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur non trouvé : " + id));
    }

    private AssureProfileDTO toProfileDTO(AssureAMU a) {
        return AssureProfileDTO.builder()
                .idAssure(a.getIdAssure()).nom(a.getNom()).prenom(a.getPrenom())
                .numeroAMU(a.getNumeroAMU()).dateNaissance(a.getDateNaissance())
                .telephone(a.getTelephone()).email(a.getEmail())
                .adresse(a.getAdresse()).statut(a.getStatut()).build();
    }

    private AdminProfileDTO toAdminDTO(Administrateur a) {
        return AdminProfileDTO.builder()
                .idAdmin(a.getIdAdmin()).nom(a.getNom()).prenom(a.getPrenom())
                .email(a.getEmail()).login(a.getLogin()).actif(a.getActif()).build();
    }

    private DemandeResponseDTO toDemandeDTO(Demande d) {
        return DemandeResponseDTO.builder()
                .idDemande(d.getIdDemande()).typeDemande(d.getTypeDemande())
                .dateDemande(d.getDateDemande()).statut(d.getStatut())
                .description(d.getDescription()).resultat(d.getResultat()).build();
    }
}
