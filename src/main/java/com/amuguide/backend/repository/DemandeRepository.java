package com.amuguide.backend.repository;


import com.amuguide.backend.entity.Demande;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemandeRepository extends JpaRepository<Demande, Long> {

        List<Demande> findByAssure_IdAssure(Long idAssure);


}
