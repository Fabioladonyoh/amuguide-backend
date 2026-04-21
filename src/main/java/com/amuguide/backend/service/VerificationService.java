package com.amuguide.backend.service;

import com.amuguide.backend.dto.VerificationResponseDTO;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.repository.PrestationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final PrestationRepository prestationRepository;

    public VerificationResponseDTO verifierParCodeActe(String codeActe) {
        Prestation prestation = prestationRepository.findByCodeActe(codeActe)
                .orElseThrow(() -> new RuntimeException("Aucune prestation trouvée pour le code acte : " + codeActe));

        return construireResultatVerification(prestation);
    }

    public List<Prestation> rechercherParMotCle(String motCle) {
        return prestationRepository.findByNomActeContainingIgnoreCase(motCle);
    }

    private VerificationResponseDTO construireResultatVerification(Prestation prestation) {
        String statut = Boolean.TRUE.equals(prestation.getPrisEnCharge()) ? "COUVERT" : "NON_COUVERT";
        String message = Boolean.TRUE.equals(prestation.getPrisEnCharge())
                ? "Cet acte est pris en charge par l'AMU."
                : "Cet acte n'est pas pris en charge par l'AMU.";

        return VerificationResponseDTO.builder()
                .statut(statut)
                .message(message)
                .codeActe(prestation.getCodeActe())
                .nomActe(prestation.getNomActe())
                .categorie(prestation.getCategorie().name())
                .prisEnCharge(prestation.getPrisEnCharge())
                .tauxCouverture(prestation.getTauxCouverture())
                .conditionsPriseEnCharge(prestation.getConditionsPriseEnCharge())
                .documentsRequis(prestation.getDocumentsRequis())
                .build();
    }
}