package com.amuguide.backend.service;

import com.amuguide.backend.dto.HospitalisationTarifDTO;
import com.amuguide.backend.entity.HospitalisationTarif;
import com.amuguide.backend.repository.HospitalisationTarifRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HospitalisationTarifService {

    private final HospitalisationTarifRepository hospitalisationTarifRepository;

    public Page<HospitalisationTarifDTO> search(String categorie, String chambre, String population, String typePrestataire, Pageable pageable) {
        return hospitalisationTarifRepository.search(categorie, chambre, population, typePrestataire, pageable)
                .map(this::toDTO);
    }

    public List<HospitalisationTarif> searchForChatbot(String categorie, String chambre, String population, String typePrestataire) {
        return hospitalisationTarifRepository.searchForChatbot(categorie, chambre, population, typePrestataire);
    }

    public HospitalisationTarifDTO toDTO(HospitalisationTarif tarif) {
        return HospitalisationTarifDTO.builder()
                .id(tarif.getId())
                .categorie(tarif.getCategorie())
                .chambre(tarif.getChambre())
                .population(tarif.getPopulation())
                .typePrestataire(tarif.getTypePrestataire())
                .dateDebut(tarif.getDateDebut())
                .tauxRemboursement(tarif.getTauxRemboursement())
                .premiereSemaine(tarif.getPremiereSemaine())
                .deuxiemeSemaine(tarif.getDeuxiemeSemaine())
                .aPartirTroisiemeSemaine(tarif.getAPartirTroisiemeSemaine())
                .createdAt(tarif.getCreatedAt())
                .updatedAt(tarif.getUpdatedAt())
                .build();
    }
}
