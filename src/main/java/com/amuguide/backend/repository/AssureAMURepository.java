package com.amuguide.backend.repository;

import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.enums.StatutAssure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AssureAMURepository extends JpaRepository<AssureAMU, Long> {

    Optional<AssureAMU> findByNumeroAMU(String numeroAMU);

    Optional<AssureAMU> findByEmail(String email);

    long countByStatut(StatutAssure statut);

    @Query("""
            select a from AssureAMU a
            where (:status is null or a.statut = :status)
              and (:search is null or :search = ''
                   or lower(a.nom) like lower(concat('%', :search, '%'))
                   or lower(a.prenom) like lower(concat('%', :search, '%'))
                   or lower(a.email) like lower(concat('%', :search, '%'))
                   or lower(a.telephone) like lower(concat('%', :search, '%'))
                   or lower(a.numeroAMU) like lower(concat('%', :search, '%')))
            """)
    Page<AssureAMU> searchAssures(String search, StatutAssure status, Pageable pageable);

}
