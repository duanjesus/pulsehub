package com.pulsehub.repository;

import com.pulsehub.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    Optional<ConversationParticipant> findByConversationIdAndUserIdAndLeftAtIsNull(Long conversationId, Long userId);

    List<ConversationParticipant> findByConversationIdAndLeftAtIsNull(Long conversationId);

    List<ConversationParticipant> findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(Long conversationId);

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(Long conversationId, Long userId);

    long countByConversationIdAndLeftAtIsNull(Long conversationId);

    @Query("select cp.conversationId from ConversationParticipant cp where cp.userId = :userId and cp.leftAt is null")
    List<Long> findActiveConversationIdsForUser(@Param("userId") Long userId);

    List<ConversationParticipant> findByConversationIdInAndLeftAtIsNull(List<Long> conversationIds);
}
