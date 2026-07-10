package com.pulsehub.entity;

import com.pulsehub.entity.enums.MessageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    /** Text body for {@code TEXT} messages; null for {@code VOICE}. */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** {@code /uploads/voice/...} path for {@code VOICE} messages; null for {@code TEXT}. */
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "attachment_duration_seconds")
    private Integer attachmentDurationSeconds;

    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        this.sentAt = LocalDateTime.now();
    }

}
