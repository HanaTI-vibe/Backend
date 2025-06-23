package com.kopo.l2q.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String roomId;
    private String userId;
    private String userName;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Enumerated(EnumType.STRING)
    private MessageType type;
    
    private LocalDateTime timestamp;
    
    public enum MessageType {
        MESSAGE, SYSTEM
    }
} 