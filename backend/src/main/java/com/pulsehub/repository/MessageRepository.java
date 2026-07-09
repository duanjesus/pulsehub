package com.pulsehub.repository;

import com.pulsehub.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

    @Query("select m.id from Message m where m.conversationId = :conversationId and m.senderId <> :readerId " +
            "and m.id not in (select mr.messageId from MessageRead mr where mr.userId = :readerId)")
    List<Long> findUnreadMessageIds(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId);

    @Query("select count(m) from Message m where m.conversationId = :conversationId and m.senderId <> :userId " +
            "and m.id not in (select mr.messageId from MessageRead mr where mr.userId = :userId)")
    long countUnreadForUser(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
