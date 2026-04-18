package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Administrateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdministrateurRepository extends JpaRepository<Administrateur, Long> {

    Optional<Administrateur> findByLogin(String login);

    Optional<Administrateur> findByEmail(String email);

}
