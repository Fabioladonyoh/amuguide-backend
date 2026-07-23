package com.amuguide.backend.repository;

import com.amuguide.backend.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    List<ChatHistory> findTop20ByUser_IdAssureOrderByDateDesc(Long userId);
}
