package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Historique;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriqueRepository extends JpaRepository<Historique, Long> {

    List<Historique> findByDemande_IdDemande(Long idDemande);

}
