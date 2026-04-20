package com.amuguide.backend.controller;

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
    public ResponseEntity<Demande> createDemande(
            @RequestParam Long assureId,
            @RequestParam(required = false) Long prestationId,
            @RequestBody Demande demande) {
        return ResponseEntity.ok(demandeService.createDemande(assureId, prestationId, demande));
    }

    @PutMapping("/{id}/resultat")
    public ResponseEntity<Demande> updateResultatDemande(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String resultat = body.get("resultat");
        StatutDemande statut = StatutDemande.valueOf(body.get("statut"));

        return ResponseEntity.ok(demandeService.updateResultatDemande(id, resultat, statut));
    }
}