package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Medicament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MedicamentRepository extends JpaRepository<Medicament, Long> {

    Optional<Medicament> findByCode(String code);

    Optional<Medicament> findByCodeIgnoreCase(String code);

    List<Medicament> findByCodeIn(Collection<String> codes);

    Optional<Medicament> findByNomIgnoreCase(String nom);

    Optional<Medicament> findByNomIgnoreCaseAndActifTrue(String nom);

    List<Medicament> findTop10ByNomContainingIgnoreCaseAndActifTrueOrderByNomAsc(String nom);

    List<Medicament> findTop10ByDciContainingIgnoreCaseAndActifTrueOrderByNomAsc(String dci);

    long countByActifTrue();

    long countByPrisEnChargeTrue();

    @Query("""
            select count(m) from Medicament m
            where (m.actif = true or m.actif is null)
              and m.statut is not null
              and m.typeMedicament is not null
              and m.baseRemboursement is not null
            """)
    long countOfficialActiveMedicaments();

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

    @Query("""
            select m from Medicament m
            where (:active is null or m.actif = :active)
              and m.statut is not null
              and m.typeMedicament is not null
              and m.baseRemboursement is not null
              and (:prisEnCharge is null or m.prisEnCharge = :prisEnCharge)
              and (:categorie is null or :categorie = '' or lower(m.categorie) = lower(:categorie))
              and (:search is null or :search = ''
                   or lower(m.nom) like lower(concat('%', :search, '%'))
                   or lower(m.code) like lower(concat('%', :search, '%'))
                   or lower(m.dci) like lower(concat('%', :search, '%')))
            """)
    Page<Medicament> searchOfficialMedicaments(String search, Boolean prisEnCharge, String categorie, Boolean active, Pageable pageable);

    @Query("""
            select m from Medicament m
            where (m.actif = true or m.actif is null)
              and m.statut is not null
              and m.typeMedicament is not null
              and m.baseRemboursement is not null
              and (:query is null or :query = ''
                   or lower(m.code) = lower(:query)
                   or lower(m.nom) like lower(concat('%', :query, '%'))
                   or lower(m.dci) like lower(concat('%', :query, '%'))
                   or lower(m.dosage) like lower(concat('%', :query, '%'))
                   or lower(m.formePharmaceutique) like lower(concat('%', :query, '%'))
                   or lower(m.groupeTherapeutique) like lower(concat('%', :query, '%')))
            order by
              case
                when lower(m.code) = lower(:query) then 0
                when lower(m.nom) = lower(:query) then 1
                when lower(m.dci) = lower(:query) then 2
                when lower(m.nom) like lower(concat(:query, '%')) then 3
                when lower(m.dci) like lower(concat(:query, '%')) then 4
                else 5
              end,
              m.nom asc
            """)
    List<Medicament> searchForChatbot(@Param("query") String query, Pageable pageable);
}
