package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Pharmacie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PharmacieRepository extends JpaRepository<Pharmacie, Long> {

    Optional<Pharmacie> findByCode(String code);

    List<Pharmacie> findByNomContainingIgnoreCase(String nom);

    List<Pharmacie> findByTelephoneContaining(String telephone);

    List<Pharmacie> findByVilleIgnoreCase(String ville);

    List<Pharmacie> findByQuartierIgnoreCase(String quartier);

    long countByActiveTrue();

    long countByAgreeeTrue();

    @Query("""
            select p from Pharmacie p
            where (:active is null or p.active = :active)
              and (:agreee is null or p.agreee = :agreee)
              and (:ville is null or :ville = '' or lower(p.ville) = lower(:ville))
              and (:quartier is null or :quartier = '' or lower(p.quartier) like lower(concat('%', :quartier, '%')))
              and (:search is null or :search = ''
                   or lower(p.code) like lower(concat('%', :search, '%'))
                   or lower(p.nom) like lower(concat('%', :search, '%'))
                   or lower(p.telephone) like lower(concat('%', :search, '%'))
                   or replace(p.telephone, ' ', '') like concat('%', replace(:search, ' ', ''), '%')
                   or lower(p.adresse) like lower(concat('%', :search, '%'))
                   or lower(p.quartier) like lower(concat('%', :search, '%'))
                   or lower(p.ville) like lower(concat('%', :search, '%')))
            """)
    Page<Pharmacie> searchPharmacies(String search, String ville, String quartier, Boolean active, Boolean agreee, Pageable pageable);
}
