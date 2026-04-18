package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Prestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrestationRepository extends JpaRepository<Prestation, Long> {

        Optional<Prestation> findByCodeActe(String codeActe);

        List<Prestation> findByNomActeContainingIgnoreCase(String nomActe);


}
