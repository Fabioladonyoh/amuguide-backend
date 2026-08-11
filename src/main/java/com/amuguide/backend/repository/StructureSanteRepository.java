package com.amuguide.backend.repository;

import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StructureSanteRepository extends JpaRepository<StructureSante, Long> {

    List<StructureSante> findByVilleIgnoreCase(String ville);

    List<StructureSante> findByType(TypeStructure type);

    List<StructureSante> findByAgrementAMUTrue();

    List<StructureSante> findByCodeIn(Collection<String> codes);

    Optional<StructureSante> findByNomAndVille(String nom, String ville);

    long countByAgrementAMUTrue();

    @Query("select count(s) from StructureSante s where s.actif = true or s.actif is null")
    long countActiveStructures();

    @Query("select count(s) from StructureSante s where s.type <> com.amuguide.backend.enums.TypeStructure.PHARMACIE")
    long countHealthStructures();

    @Query("select count(s) from StructureSante s where s.type <> com.amuguide.backend.enums.TypeStructure.PHARMACIE and (s.actif = true or s.actif is null)")
    long countActiveHealthStructures();

    @Query("""
            select count(s) from StructureSante s
            where s.code is not null and s.code <> ''
              and s.agrementAMU = true
              and s.typeOfficiel in ('PHARMACIE', 'DEPOT PHARMACIE', 'DEPOT PHARMACEUTIQUE')
            """)
    long countOfficialPharmacies();

    @Query("""
            select s from StructureSante s
            where s.code is not null and s.code <> ''
              and s.agrementAMU = true
              and s.typeOfficiel in ('PHARMACIE', 'DEPOT PHARMACIE', 'DEPOT PHARMACEUTIQUE')
            """)
    List<StructureSante> findOfficialPharmacies(Sort sort);

    @Query("""
            select s from StructureSante s
            where s.code is not null and s.code <> ''
              and s.agrementAMU = true
              and s.typeOfficiel in ('PHARMACIE', 'DEPOT PHARMACIE', 'DEPOT PHARMACEUTIQUE')
              and (:search is null or :search = ''
                   or lower(s.code) like lower(concat('%', :search, '%'))
                   or lower(s.nom) like lower(concat('%', :search, '%'))
                   or lower(s.adresse) like lower(concat('%', :search, '%'))
                   or lower(s.region) like lower(concat('%', :search, '%'))
                   or lower(s.typeOfficiel) like lower(concat('%', :search, '%')))
            """)
    Page<StructureSante> searchOfficialPharmacies(@Param("search") String search, Pageable pageable);

    @Query("""
            select s from StructureSante s
            where s.code is not null and s.code <> ''
              and s.agrementAMU = true
              and (s.actif = true or s.actif is null)
              and s.typeOfficiel not in ('PHARMACIE', 'DEPOT PHARMACIE', 'DEPOT PHARMACEUTIQUE')
            """)
    List<StructureSante> findOfficialHealthStructures();

    @Query("""
            select s from StructureSante s
            where (:active is null or s.actif = :active or (:active = true and s.actif is null))
              and s.code is not null and s.code <> ''
              and s.type <> com.amuguide.backend.enums.TypeStructure.PHARMACIE
              and s.typeOfficiel not in ('PHARMACIE', 'DEPOT PHARMACIE', 'DEPOT PHARMACEUTIQUE')
              and (:agrement is null or s.agrementAMU = :agrement)
              and (:ville is null or :ville = '' or lower(s.ville) = lower(:ville))
              and (:type is null or s.type = :type)
               and (:search is null or :search = ''
                   or lower(s.nom) like lower(concat('%', :search, '%'))
                   or lower(s.adresse) like lower(concat('%', :search, '%'))
                   or lower(s.ville) like lower(concat('%', :search, '%'))
                   or lower(s.telephone) like lower(concat('%', :search, '%'))
                   or replace(s.telephone, ' ', '') like concat('%', replace(:search, ' ', ''), '%')
                   or lower(function('translate', s.nom, 'éèêëàáâäôöîïûüùçÉÈÊËÀÁÂÄÔÖÎÏÛÜÙÇ', 'eeeeaaaaooiiuuucEEEEAAAAOOIIUUUC'))
                        like lower(concat('%', function('translate', :search, 'éèêëàáâäôöîïûüùçÉÈÊËÀÁÂÄÔÖÎÏÛÜÙÇ', 'eeeeaaaaooiiuuucEEEEAAAAOOIIUUUC'), '%'))
                   or lower(function('translate', s.adresse, 'éèêëàáâäôöîïûüùçÉÈÊËÀÁÂÄÔÖÎÏÛÜÙÇ', 'eeeeaaaaooiiuuucEEEEAAAAOOIIUUUC'))
                        like lower(concat('%', function('translate', :search, 'éèêëàáâäôöîïûüùçÉÈÊËÀÁÂÄÔÖÎÏÛÜÙÇ', 'eeeeaaaaooiiuuucEEEEAAAAOOIIUUUC'), '%'))
                   or lower(function('translate', s.ville, 'éèêëàáâäôöîïûüùçÉÈÊËÀÁÂÄÔÖÎÏÛÜÙÇ', 'eeeeaaaaooiiuuucEEEEAAAAOOIIUUUC'))
                        like lower(concat('%', function('translate', :search, 'éèêëàáâäôöîïûüùçÉÈÊËÀÁÂÄÔÖÎÏÛÜÙÇ', 'eeeeaaaaooiiuuucEEEEAAAAOOIIUUUC'), '%'))
                   or lower(s.region) like lower(concat('%', :search, '%'))
                   or lower(cast(s.type as string)) like lower(concat('%', :search, '%')))
            """)
    Page<StructureSante> searchStructures(String search, String ville, TypeStructure type, Boolean active, Boolean agrement, Pageable pageable);
}
