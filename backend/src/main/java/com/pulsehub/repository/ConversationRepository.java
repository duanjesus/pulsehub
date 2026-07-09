package com.pulsehub.repository;

import com.pulsehub.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

    List<Conversation> findByUserOneIdOrUserTwoIdOrderByIdDesc(Long userOneId, Long userTwoId);
}
