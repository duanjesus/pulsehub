package com.pulsehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Private 1:1 conversation. {@code userOneId} is always the lower user id of the
 * pair — enforced in the service layer — so a (userOneId, userTwoId) pair is
 * unique regardless of who started the conversation.
 */
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"user_one_id", "user_two_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_one_id", nullable = false)
    private Long userOneId;

    @Column(name = "user_two_id", nullable = false)
    private Long userTwoId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean hasParticipant(Long userId) {
        return userOneId.equals(userId) || userTwoId.equals(userId);
    }

    public Long otherParticipant(Long userId) {
        return userOneId.equals(userId) ? userTwoId : userOneId;
    }

}
