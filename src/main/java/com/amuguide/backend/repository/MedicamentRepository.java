package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Medicament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MedicamentRepository extends JpaRepository<Medicament, Long> {

    Optional<Medicament> findByCode(String code);

    Optional<Medicament> findByNomIgnoreCase(String nom);

    long countByActifTrue();

    long countByPrisEnChargeTrue();

    @Query("""
            select m from Medicament m
            where (:active is null or m.actif = :active)
              and (:prisEnCharge is null or m.prisEnCharge = :prisEnCharge)
              and (:categorie is null or :categorie = '' or lower(m.categorie) = lower(:categorie))
              and (:search is null or :search = ''
                   or lower(m.nom) like lower(concat('%', :search, '%'))
                   or lower(m.code) like lower(concat('%', :search, '%'))
                   or lower(m.dci) like lower(concat('%', :search, '%')))
            """)
    Page<Medicament> searchMedicaments(String search, Boolean prisEnCharge, String categorie, Boolean active, Pageable pageable);
}
