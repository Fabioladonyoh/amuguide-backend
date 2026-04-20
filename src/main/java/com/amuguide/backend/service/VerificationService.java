package com.amuguide.backend.service;

import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.repository.PrestationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

    @Service
    @RequiredArgsConstructor
    public class VerificationService {

        private final PrestationRepository prestationRepository;

        public Map<String, Object> verifierParCodeActe(String codeActe) {
            Prestation prestation = prestationRepository.findByCodeActe(codeActe)
                    .orElseThrow(() -> new RuntimeException("Aucune prestation trouvée pour le code acte : " + codeActe));

            return construireResultatVerification(prestation);
        }

        public List<Prestation> rechercherParMotCle(String motCle) {
            return prestationRepository.findByNomActeContainingIgnoreCase(motCle);
        }

        private Map<String, Object> construireResultatVerification(Prestation prestation) {
            Map<String, Object> resultat = new HashMap<>();
            resultat.put("codeActe", prestation.getCodeActe());
            resultat.put("nomActe", prestation.getNomActe());
            resultat.put("categorie", prestation.getCategorie());
            resultat.put("prisEnCharge", prestation.getPrisEnCharge());
            resultat.put("tauxCouverture", prestation.getTauxCouverture());
            resultat.put("conditionsPriseEnCharge", prestation.getConditionsPriseEnCharge());
            resultat.put("documentsRequis", prestation.getDocumentsRequis());

            if (Boolean.TRUE.equals(prestation.getPrisEnCharge())) {
                resultat.put("statut", "COUVERT");
                resultat.put("message", "Cet acte est pris en charge par l'AMU.");
            } else {
                resultat.put("statut", "NON_COUVERT");
                resultat.put("message", "Cet acte n'est pas pris en charge par l'AMU.");
            }

            return resultat;
        }


}
