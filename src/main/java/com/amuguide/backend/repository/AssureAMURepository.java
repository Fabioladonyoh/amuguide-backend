package com.amuguide.backend.repository;

import com.amuguide.backend.entity.AssureAMU;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssureAMURepository extends JpaRepository<AssureAMU, Long> {

    Optional<AssureAMU> findByNumeroAMU(String numeroAMU);

    Optional<AssureAMU> findByEmail(String email);

}
