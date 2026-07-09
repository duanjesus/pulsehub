package com.pulsehub.repository;

import com.pulsehub.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    @Modifying
    @Query("update Message m set m.readAt = CURRENT_TIMESTAMP " +
            "where m.conversationId = :conversationId and m.senderId <> :readerId and m.readAt is null")
    int markConversationAsRead(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId);
}
