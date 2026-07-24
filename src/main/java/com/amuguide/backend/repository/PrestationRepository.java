package com.amuguide.backend.repository;

import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.enums.CategorieActe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PrestationRepository extends JpaRepository<Prestation, Long> {

        Optional<Prestation> findByCodeActe(String codeActe);

        List<Prestation> findByNomActeContainingIgnoreCase(String nomActe);

        long countByPrisEnChargeTrue();

        List<Prestation> findByPrisEnChargeTrue();

        @Query("""
                select p from Prestation p
                where (:active is null or p.prisEnCharge = :active)
                  and (:categorie is null or p.categorie = :categorie)
                  and (:search is null or :search = ''
                       or lower(p.nomActe) like lower(concat('%', :search, '%'))
                       or lower(p.codeActe) like lower(concat('%', :search, '%'))
                       or lower(p.description) like lower(concat('%', :search, '%')))
                """)
        Page<Prestation> searchPrestations(String search, Boolean active, CategorieActe categorie, Pageable pageable);

}
