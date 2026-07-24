package com.amuguide.backend.repository;

import com.amuguide.backend.entity.ChatHistory;
import com.amuguide.backend.chat.nlp.ChatIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    List<ChatHistory> findTop20ByUser_IdAssureOrderByDateDesc(Long userId);

    List<ChatHistory> findByUser_IdAssureOrderByDateDesc(Long userId);

    Optional<ChatHistory> findByIdAndUser_IdAssure(Long id, Long userId);

    long countByUser_IdAssure(Long userId);

    long countByDateAfter(LocalDateTime date);

    @Query("select c.intention as intention, count(c) as total from ChatHistory c group by c.intention order by count(c) desc")
    List<IntentCount> countByIntent();

    @Query("select cast(c.date as date) as period, count(c) as total from ChatHistory c group by cast(c.date as date) order by cast(c.date as date) desc")
    List<PeriodCount> countByDay();

    interface IntentCount {
        ChatIntent getIntention();
        long getTotal();
    }

    interface PeriodCount {
        Object getPeriod();
        long getTotal();
    }
}
