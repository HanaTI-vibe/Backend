package com.kopo.l2q.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Participant {
    @Id
    private String id;
    private String name;
    private int score = 0;
    private boolean isReady = false;
    private boolean submitted = false;
    private String socketId;
    @ManyToOne
    @JoinColumn(name = "room_id")
    @JsonIgnore
    private Room room;
} 