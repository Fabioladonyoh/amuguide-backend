package com.amuguide.backend.controller;

import com.amuguide.backend.dto.DemandeRequestDTO;
import com.amuguide.backend.dto.DemandeResponseDTO;
import com.amuguide.backend.entity.Demande;
import com.amuguide.backend.entity.Historique;
import com.amuguide.backend.enums.StatutDemande;
import com.amuguide.backend.service.DemandeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demandes")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DemandeController {

    private final DemandeService demandeService;

    @GetMapping
    public ResponseEntity<List<Demande>> getAllDemandes() {
        return ResponseEntity.ok(demandeService.getAllDemandes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Demande> getDemandeById(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.getDemandeById(id));
    }

    @GetMapping("/assure/{assureId}")
    public ResponseEntity<List<Demande>> getDemandesByAssure(@PathVariable Long assureId) {
        return ResponseEntity.ok(demandeService.getDemandesByAssure(assureId));
    }

    @GetMapping("/{id}/historiques")
    public ResponseEntity<List<Historique>> getHistoriquesByDemande(@PathVariable Long id) {
        return ResponseEntity.ok(demandeService.getHistoriquesByDemande(id));
    }

    @PostMapping
    public ResponseEntity<DemandeResponseDTO> createDemande(@RequestBody DemandeRequestDTO request) {
        Demande demande = Demande.builder()
                .typeDemande(request.getTypeDemande())
                .description(request.getDescription())
                .build();

        Demande saved = demandeService.createDemande(
                request.getAssureId(),
                request.getPrestationId(),
                demande
        );

        return ResponseEntity.ok(mapToResponse(saved));
    }

    @PutMapping("/{id}/resultat")
    public ResponseEntity<DemandeResponseDTO> updateResultatDemande(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String resultat = body.get("resultat");
        StatutDemande statut = StatutDemande.valueOf(body.get("statut"));

        Demande updated = demandeService.updateResultatDemande(id, resultat, statut);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    private DemandeResponseDTO mapToResponse(Demande demande) {
        return DemandeResponseDTO.builder()
                .idDemande(demande.getIdDemande())
                .typeDemande(demande.getTypeDemande())
                .dateDemande(demande.getDateDemande())
                .statut(demande.getStatut())
                .description(demande.getDescription())
                .resultat(demande.getResultat())
                .build();
    }
}