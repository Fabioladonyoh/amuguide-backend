package com.amuguide.backend.repository;

import com.amuguide.backend.entity.FaqChatbot;
import com.amuguide.backend.enums.FaqChatbotCategorie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FaqChatbotRepository extends JpaRepository<FaqChatbot, Long> {

    List<FaqChatbot> findByActifTrue();

    List<FaqChatbot> findByCategorieAndActifTrue(FaqChatbotCategorie categorie);

    Optional<FaqChatbot> findByCode(String code);
}
