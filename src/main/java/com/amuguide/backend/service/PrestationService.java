package com.amuguide.backend.service;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.repository.PrestationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

    @Service
    @RequiredArgsConstructor
    public class PrestationService {

        private final PrestationRepository prestationRepository;

        public List<Prestation> getAllPrestations() {
            return prestationRepository.findAll();
        }

        public Prestation getPrestationById(Long id) {
            return prestationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Prestation introuvable avec l'id : " + id));
        }

        public Prestation getPrestationByCodeActe(String codeActe) {
            return prestationRepository.findByCodeActe(codeActe)
                    .orElseThrow(() -> new RuntimeException("Prestation introuvable avec le code acte : " + codeActe));
        }

        public List<Prestation> searchPrestationsByNom(String nomActe) {
            return prestationRepository.findByNomActeContainingIgnoreCase(nomActe);
        }

        public Prestation savePrestation(Prestation prestation) {
            return prestationRepository.save(prestation);
        }

        public Prestation updatePrestation(Long id, Prestation updatedPrestation) {
            Prestation existing = getPrestationById(id);

            existing.setCodeActe(updatedPrestation.getCodeActe());
            existing.setNomActe(updatedPrestation.getNomActe());
            existing.setCategorie(updatedPrestation.getCategorie());
            existing.setDescription(updatedPrestation.getDescription());
            existing.setTauxCouverture(updatedPrestation.getTauxCouverture());
            existing.setPrisEnCharge(updatedPrestation.getPrisEnCharge());
            existing.setConditionsPriseEnCharge(updatedPrestation.getConditionsPriseEnCharge());
            existing.setDocumentsRequis(updatedPrestation.getDocumentsRequis());

            return prestationRepository.save(existing);
        }

        public void deletePrestation(Long id) {
            Prestation prestation = getPrestationById(id);
            prestationRepository.delete(prestation);
        }


}
