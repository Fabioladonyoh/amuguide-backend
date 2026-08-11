package com.amuguide.backend.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MedicationDTOOfficialFieldsTest {

    @Test
    void exposesOfficialMedicationFields() {
        MedicationDTO dto = MedicationDTO.builder()
                .code("0002601")
                .nom("DOLIPRANE COMP 500MG")
                .statut("REMBOURSABLE")
                .typeMedicament("SPECIALITE")
                .groupeTherapeutique("ANTALGIQUE")
                .prixPublic(new BigDecimal("1000"))
                .baseRemboursement(new BigDecimal("800"))
                .partInam(new BigDecimal("640"))
                .partBeneficiaire(new BigDecimal("160"))
                .tauxCouverture(80.0)
                .build();

        assertThat(dto.getStatut()).isEqualTo("REMBOURSABLE");
        assertThat(dto.getTypeMedicament()).isEqualTo("SPECIALITE");
        assertThat(dto.getGroupeTherapeutique()).isEqualTo("ANTALGIQUE");
        assertThat(dto.getPrixPublic()).isEqualByComparingTo("1000");
        assertThat(dto.getBaseRemboursement()).isEqualByComparingTo("800");
        assertThat(dto.getPartInam()).isEqualByComparingTo("640");
        assertThat(dto.getPartBeneficiaire()).isEqualByComparingTo("160");
        assertThat(dto.getTauxCouverture()).isEqualTo(80.0);
    }
}
