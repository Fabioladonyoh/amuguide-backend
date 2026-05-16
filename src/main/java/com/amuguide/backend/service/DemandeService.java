package com.amuguide.backend.service;

import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.entity.Demande;
import com.amuguide.backend.entity.Historique;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.enums.StatutDemande;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.DemandeRepository;
import com.amuguide.backend.repository.HistoriqueRepository;
import com.amuguide.backend.repository.PrestationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

    @Service
    @RequiredArgsConstructor
    public class DemandeService {

        private final DemandeRepository demandeRepository;
        private final AssureAMURepository assureAMURepository;
        private final PrestationRepository prestationRepository;
        private final HistoriqueRepository historiqueRepository;

        public Demande createDemande(Long assureId, Long prestationId, Demande demande) {
            AssureAMU assure = assureAMURepository.findById(assureId)
                    .orElseThrow(() -> new RuntimeException("Assuré introuvable avec l'id : " + assureId));

            demande.setAssure(assure);

            if (prestationId != null) {
                Prestation prestation = prestationRepository.findById(prestationId)
                        .orElseThrow(() -> new RuntimeException("Prestation introuvable avec l'id : " + prestationId));
                demande.setPrestation(prestation);
            }

            Demande savedDemande = demandeRepository.save(demande);

            Historique historique = Historique.builder()
                    .action("CREATION")
                    .details("Demande créée avec succès")
                    .demande(savedDemande)
                    .build();

            historiqueRepository.save(historique);

            return savedDemande;
        }

        public Demande getDemandeById(Long id) {
            return demandeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Demande introuvable avec l'id : " + id));
        }

        public List<Demande> getDemandesByAssure(Long assureId) {
            return demandeRepository.findByAssure_IdAssure(assureId);
        }

        public List<Demande> getAllDemandes() {
            return demandeRepository.findAll();
        }

        public Demande updateResultatDemande(Long demandeId, String resultat, StatutDemande statut) {
            Demande demande = getDemandeById(demandeId);
            demande.setResultat(resultat);
            demande.setStatut(statut);

            Demande updatedDemande = demandeRepository.save(demande);

            Historique historique = Historique.builder()
                    .action("TRAITEMENT")
                    .details("Résultat mis à jour : " + resultat)
                    .demande(updatedDemande)
                    .build();

            historiqueRepository.save(historique);

            return updatedDemande;
        }

        public List<Historique> getHistoriquesByDemande(Long demandeId) {
            return historiqueRepository.findByDemande_IdDemande(demandeId);
        }

        public boolean appartientAAssure(Long demandeId, Long assureId) {
            return demandeRepository.findByIdDemandeAndAssure_IdAssure(demandeId, assureId).isPresent();
        }

        public void supprimerDemande(Long id) {
            demandeRepository.delete(getDemandeById(id));
        }
}
