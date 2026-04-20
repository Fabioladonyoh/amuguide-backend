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
            return structureSanteRepository.findAll();
        }

        public StructureSante getStructureById(Long id) {
            return structureSanteRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Structure de santé introuvable avec l'id : " + id));
        }

        public List<StructureSante> getStructuresByVille(String ville) {
            return structureSanteRepository.findByVilleIgnoreCase(ville);
        }

        public List<StructureSante> getStructuresByType(TypeStructure type) {
            return structureSanteRepository.findByType(type);
        }

        public List<StructureSante> getStructuresAgrees() {
            return structureSanteRepository.findByAgrementAMUTrue();
        }

        public StructureSante saveStructure(StructureSante structureSante) {
            return structureSanteRepository.save(structureSante);
        }

        public StructureSante updateStructure(Long id, StructureSante updatedStructure) {
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
            StructureSante structure = getStructureById(id);
            structureSanteRepository.delete(structure);
        }

        public List<StructureSante> findStructuresProches(double latitude, double longitude, double rayonKm) {
            return getStructuresAgrees().stream()
                    .filter(structure -> calculerDistance(latitude, longitude, structure.getLatitude(), structure.getLongitude()) <= rayonKm)
                    .toList();
        }

        private double calculerDistance(double lat1, double lon1, double lat2, double lon2) {
            final int R = 6371;

            double latDistance = Math.toRadians(lat2 - lat1);
            double lonDistance = Math.toRadians(lon2 - lon1);

            double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            return R * c;
        }


}
