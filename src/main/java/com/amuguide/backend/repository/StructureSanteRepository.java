package com.amuguide.backend.repository;

import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StructureSanteRepository extends JpaRepository<StructureSante, Long> {

    List<StructureSante> findByVilleIgnoreCase(String ville);

    List<StructureSante> findByType(TypeStructure type);

    List<StructureSante> findByAgrementAMUTrue();

    Optional<StructureSante> findByNomAndVille(String nom, String ville);

    long countByAgrementAMUTrue();

    @Query("select count(s) from StructureSante s where s.actif = true or s.actif is null")
    long countActiveStructures();

    @Query("""
            select s from StructureSante s
            where (:active is null or s.actif = :active or (:active = true and s.actif is null))
              and (:agrement is null or s.agrementAMU = :agrement)
              and (:ville is null or :ville = '' or lower(s.ville) = lower(:ville))
              and (:type is null or s.type = :type)
              and (:search is null or :search = ''
                   or lower(s.nom) like lower(concat('%', :search, '%'))
                   or lower(s.adresse) like lower(concat('%', :search, '%'))
                   or lower(s.ville) like lower(concat('%', :search, '%'))
                   or lower(s.region) like lower(concat('%', :search, '%'))
                   or lower(cast(s.type as string)) like lower(concat('%', :search, '%')))
            """)
    Page<StructureSante> searchStructures(String search, String ville, TypeStructure type, Boolean active, Boolean agrement, Pageable pageable);
}
