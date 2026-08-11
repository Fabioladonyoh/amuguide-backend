package com.amuguide.backend.service;

import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.StructureSanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StructureSanteService {

    private final StructureSanteRepository structureSanteRepository;

    public List<StructureSante> getAllStructures() {
        return structureSanteRepository.findOfficialHealthStructures();
    }

    public StructureSante getStructureById(Long id) {
        return structureSanteRepository.findById(id)
                .filter(this::isHealthStructure)
                .filter(this::isOfficialStructure)
                .orElseThrow(() -> new RuntimeException("Structure de sante introuvable avec l'id : " + id));
    }

    public List<StructureSante> getStructuresByVille(String ville) {
        return structureSanteRepository.findByVilleIgnoreCase(ville).stream()
                .filter(this::isHealthStructure)
                .filter(this::isOfficialStructure)
                .toList();
    }

    public List<StructureSante> getStructuresByType(TypeStructure type) {
        if (type == TypeStructure.PHARMACIE) {
            return List.of();
        }
        return structureSanteRepository.findByType(type).stream()
                .filter(this::isOfficialStructure)
                .toList();
    }

    public List<StructureSante> getStructuresAgrees() {
        return structureSanteRepository.findByAgrementAMUTrue().stream()
                .filter(this::isHealthStructure)
                .filter(this::isOfficialStructure)
                .toList();
    }

    public StructureSante saveStructure(StructureSante structureSante) {
        if (structureSante.getType() == TypeStructure.PHARMACIE) {
            throw new IllegalArgumentException("Les pharmacies doivent etre gerees via le module Pharmacie");
        }
        return structureSanteRepository.save(structureSante);
    }

    public StructureSante updateStructure(Long id, StructureSante updatedStructure) {
        if (updatedStructure.getType() == TypeStructure.PHARMACIE) {
            throw new IllegalArgumentException("Les pharmacies doivent etre gerees via le module Pharmacie");
        }
        StructureSante existing = getStructureById(id);

        existing.setNom(updatedStructure.getNom());
        existing.setType(updatedStructure.getType());
        existing.setAdresse(updatedStructure.getAdresse());
        existing.setVille(updatedStructure.getVille());
        existing.setTelephone(updatedStructure.getTelephone());
        existing.setLatitude(updatedStructure.getLatitude());
        existing.setLongitude(updatedStructure.getLongitude());
        existing.setAgrementAMU(updatedStructure.getAgrementAMU());
        existing.setSpecialites(updatedStructure.getSpecialites());
        existing.setHoraires(updatedStructure.getHoraires());

        return structureSanteRepository.save(existing);
    }

    public void deleteStructure(Long id) {
        structureSanteRepository.delete(getStructureById(id));
    }

    public List<StructureSante> findStructuresProches(double latitude, double longitude, double rayonKm) {
        return getStructuresAgrees().stream()
                .filter(structure -> structure.getLatitude() != null && structure.getLongitude() != null)
                .filter(structure -> calculerDistance(latitude, longitude, structure.getLatitude(), structure.getLongitude()) <= rayonKm)
                .toList();
    }

    private boolean isHealthStructure(StructureSante structure) {
        return structure.getType() != TypeStructure.PHARMACIE;
    }

    private boolean isOfficialStructure(StructureSante structure) {
        return structure.getCode() != null && !structure.getCode().isBlank();
    }

    private double calculerDistance(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusKm = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
