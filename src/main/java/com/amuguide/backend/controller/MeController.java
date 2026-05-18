package com.amuguide.backend.controller;

import com.amuguide.backend.dto.AssureProfileDTO;
import com.amuguide.backend.dto.DemandeResponseDTO;
import com.amuguide.backend.dto.MeDemandeRequestDTO;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.entity.Demande;
import com.amuguide.backend.entity.Historique;
import com.amuguide.backend.exception.ResourceNotFoundException;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.service.DemandeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Espace Assuré", description = "Accès aux données personnelles de l'assuré connecté")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final AssureAMURepository assureAMURepository;
    private final DemandeService demandeService;

    // ── Helpers ────────────────────────────────────────────────────────────

    private AssureAMU getAuthenticatedAssure(Authentication auth) {
        String username = auth.getName(); // format : "ASSURE:AMU001"
        String numeroAMU = username.substring(7);
        return assureAMURepository.findByNumeroAMU(numeroAMU)
                .orElseThrow(() -> new ResourceNotFoundException("Assuré non trouvé"));
    }

    private void verifierAppartenance(Demande demande, AssureAMU assure) {
        // Comparaison des IDs pour éviter le chargement lazy de l'entité assure
        if (!demandeService.appartientAAssure(demande.getIdDemande(), assure.getIdAssure())) {
            throw new AccessDeniedException("Cette demande ne vous appartient pas");
        }
    }

    // ── Profil ─────────────────────────────────────────────────────────────

    @GetMapping("/profil")
    @Operation(summary = "Consulter mon profil")
    public ResponseEntity<AssureProfileDTO> getProfil(Authentication auth) {
        AssureAMU assure = getAuthenticatedAssure(auth);
        return ResponseEntity.ok(toProfileDTO(assure));
    }

    // ── Demandes ───────────────────────────────────────────────────────────

    @GetMapping("/demandes")
    @Operation(summary = "Lister mes demandes")
    public ResponseEntity<List<DemandeResponseDTO>> getMesDemandes(Authentication auth) {
        AssureAMU assure = getAuthenticatedAssure(auth);
        List<DemandeResponseDTO> response = demandeService.getDemandesByAssure(assure.getIdAssure())
                .stream()
                .map(this::toDemandeDTO)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/demandes/{id}")
    @Operation(summary = "Consulter une de mes demandes")
    public ResponseEntity<DemandeResponseDTO> getMaDemande(@PathVariable Long id, Authentication auth) {
        AssureAMU assure = getAuthenticatedAssure(auth);
        Demande demande = demandeService.getDemandeById(id);
        verifierAppartenance(demande, assure);
        return ResponseEntity.ok(toDemandeDTO(demande));
    }

    @PostMapping("/demandes")
    @Operation(summary = "Créer une demande (mon identité est extraite du token)")
    public ResponseEntity<DemandeResponseDTO> creerDemande(
            @Valid @RequestBody MeDemandeRequestDTO request, Authentication auth) {

        AssureAMU assure = getAuthenticatedAssure(auth);

        Demande demande = Demande.builder()
                .typeDemande(request.getTypeDemande())
                .description(request.getDescription())
                .build();

        Demande saved = demandeService.createDemande(
                assure.getIdAssure(), request.getPrestationId(), demande
        );
        return ResponseEntity.ok(toDemandeDTO(saved));
    }

    @GetMapping("/demandes/{id}/historiques")
    @Operation(summary = "Consulter l'historique d'une de mes demandes")
    public ResponseEntity<List<Historique>> getHistoriques(@PathVariable Long id, Authentication auth) {
        AssureAMU assure = getAuthenticatedAssure(auth);
        Demande demande = demandeService.getDemandeById(id);
        verifierAppartenance(demande, assure);
        return ResponseEntity.ok(demandeService.getHistoriquesByDemande(id));
    }

    // ── Mappers ────────────────────────────────────────────────────────────

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
                .build();
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
