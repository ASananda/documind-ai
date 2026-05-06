package com.example.docbot.repository;

import com.example.docbot.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    List<ConversationEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<ConversationEntity> findByIdAndUserId(String id, String userId);
}
