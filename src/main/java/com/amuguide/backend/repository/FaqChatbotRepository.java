package com.amuguide.backend.repository;

import com.amuguide.backend.entity.FaqChatbot;
import com.amuguide.backend.enums.FaqChatbotCategorie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FaqChatbotRepository extends JpaRepository<FaqChatbot, Long> {

    List<FaqChatbot> findByActifTrue();

    List<FaqChatbot> findByCategorieAndActifTrue(FaqChatbotCategorie categorie);

    Optional<FaqChatbot> findByCode(String code);

    Optional<FaqChatbot> findByQuestionIgnoreCase(String question);

    long countByActifTrue();

    @Query("""
            select f from FaqChatbot f
            where (:active is null or f.actif = :active)
              and (:categorie is null or f.categorie = :categorie)
              and (:search is null or :search = ''
                   or lower(f.question) like lower(concat('%', :search, '%'))
                   or lower(f.reponse) like lower(concat('%', :search, '%'))
                   or lower(f.motsCles) like lower(concat('%', :search, '%')))
            """)
    Page<FaqChatbot> searchFaqs(String search, FaqChatbotCategorie categorie, Boolean active, Pageable pageable);
}
