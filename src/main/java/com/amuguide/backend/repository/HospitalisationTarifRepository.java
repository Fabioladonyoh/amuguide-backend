package com.amuguide.backend.repository;

import com.amuguide.backend.entity.HospitalisationTarif;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HospitalisationTarifRepository extends JpaRepository<HospitalisationTarif, Long> {

    @Query("""
            select h from HospitalisationTarif h
            where lower(h.categorie) = lower(:categorie)
              and lower(h.chambre) = lower(:chambre)
              and lower(h.population) = lower(:population)
              and lower(h.typePrestataire) = lower(:typePrestataire)
              and h.dateDebut = :dateDebut
            """)
    Optional<HospitalisationTarif> findByNaturalKey(
            @Param("categorie") String categorie,
            @Param("chambre") String chambre,
            @Param("population") String population,
            @Param("typePrestataire") String typePrestataire,
            @Param("dateDebut") LocalDate dateDebut);

    @Query("""
            select h from HospitalisationTarif h
            where (:categorie is null or :categorie = '' or lower(h.categorie) like lower(concat('%', :categorie, '%')))
              and (:chambre is null or :chambre = '' or lower(h.chambre) like lower(concat('%', :chambre, '%')))
              and (:population is null or :population = '' or lower(h.population) like lower(concat('%', :population, '%')))
              and (:typePrestataire is null or :typePrestataire = '' or lower(h.typePrestataire) = lower(:typePrestataire))
            """)
    Page<HospitalisationTarif> search(
            @Param("categorie") String categorie,
            @Param("chambre") String chambre,
            @Param("population") String population,
            @Param("typePrestataire") String typePrestataire,
            Pageable pageable);

    @Query("""
            select h from HospitalisationTarif h
            where (:categorie is null or :categorie = '' or lower(h.categorie) like lower(concat('%', :categorie, '%')))
              and (:chambre is null or :chambre = '' or lower(h.chambre) like lower(concat('%', :chambre, '%')))
              and (:population is null or :population = '' or lower(h.population) like lower(concat('%', :population, '%')))
              and (:typePrestataire is null or :typePrestataire = '' or lower(h.typePrestataire) = lower(:typePrestataire))
            order by h.typePrestataire asc, h.chambre asc, h.population asc, h.dateDebut desc
            """)
    List<HospitalisationTarif> searchForChatbot(
            @Param("categorie") String categorie,
            @Param("chambre") String chambre,
            @Param("population") String population,
            @Param("typePrestataire") String typePrestataire);
}
