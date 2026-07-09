package com.pulsehub.repository;

import com.pulsehub.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.id = :id and n.userId = :userId and n.readAt is null")
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.userId = :userId and n.readAt is null")
    int markAllAsRead(@Param("userId") Long userId);
}
