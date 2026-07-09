package com.pulsehub.entity;

import com.pulsehub.entity.enums.ConversationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A conversation is either {@code DIRECT} (exactly 2 participants, deduplicated
 * via {@code directKey} — see {@link com.pulsehub.service.impl.ConversationServiceImpl})
 * or {@code GROUP} (named, N participants with OWNER/MEMBER roles). The actual
 * roster lives in {@link ConversationParticipant}, not on this entity.
 */
@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConversationType type = ConversationType.DIRECT;

    @Column(length = 100)
    private String name;

    @Column(name = "created_by")
    private Long createdBy;

    /** Only set for DIRECT conversations: {@code "<lowerUserId>_<higherUserId>"}, unique. */
    @Column(name = "direct_key", length = 50)
    private String directKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isDirect() {
        return type == ConversationType.DIRECT;
    }

}
