package com.amuguide.backend.chat.service;

import com.amuguide.backend.entity.Medicament;

import java.math.BigDecimal;

public record MedicationEvidence(
        Long id,
        String code,
        String nom,
        String dci,
        String dosage,
        String formePharmaceutique,
        String statut,
        String typeMedicament,
        String groupeTherapeutique,
        BigDecimal prixPublic,
        BigDecimal baseRemboursement,
        Double tauxCouverture,
        BigDecimal partInam,
        BigDecimal partBeneficiaire,
        Boolean prisEnCharge
) {
    public static MedicationEvidence from(Medicament medicament) {
        return new MedicationEvidence(
                medicament.getId(),
                medicament.getCode(),
                medicament.getNom(),
                medicament.getDci(),
                medicament.getDosage(),
                medicament.getFormePharmaceutique(),
                medicament.getStatut(),
                medicament.getTypeMedicament(),
                medicament.getGroupeTherapeutique(),
                medicament.getPrixPublic(),
                medicament.getBaseRemboursement(),
                medicament.getTauxCouverture(),
                medicament.getPartInam(),
                medicament.getPartBeneficiaire(),
                medicament.getPrisEnCharge()
        );
    }
}
