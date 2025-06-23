package com.kopo.l2q.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
public class Room {
    @Id
    private String id;
    @Column(unique = true, nullable = false)
    private String inviteCode;
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<Question> questions;
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<Participant> participants;
    private int currentQuestion = 0;
    private int timeLimit;
    private LocalDateTime createdAt;
    private boolean isMock;
    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.WAITING;

    public enum RoomStatus {
        WAITING, ACTIVE, FINISHED
    }
} 