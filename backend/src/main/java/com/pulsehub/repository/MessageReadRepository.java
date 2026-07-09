package com.pulsehub.repository;

import com.pulsehub.entity.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {

    List<MessageRead> findByMessageIdIn(List<Long> messageIds);

    boolean existsByMessageIdAndUserId(Long messageId, Long userId);
}
