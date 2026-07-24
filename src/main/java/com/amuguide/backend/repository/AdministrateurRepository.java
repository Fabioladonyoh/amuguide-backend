package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Administrateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AdministrateurRepository extends JpaRepository<Administrateur, Long> {

    Optional<Administrateur> findByLogin(String login);

    Optional<Administrateur> findByEmail(String email);

    long countByActifTrue();

    @Query("""
            select a from Administrateur a
            where (:active is null or a.actif = :active)
              and (:search is null or :search = ''
                   or lower(a.nom) like lower(concat('%', :search, '%'))
                   or lower(a.prenom) like lower(concat('%', :search, '%'))
                   or lower(a.email) like lower(concat('%', :search, '%'))
                   or lower(a.login) like lower(concat('%', :search, '%')))
            """)
    Page<Administrateur> searchAdministrateurs(String search, Boolean active, Pageable pageable);
}
