package com.kopo.l2q.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Data
public class Question {
    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    private QuestionType type;
    @Column(columnDefinition = "TEXT")
    private String question;
    @ElementCollection
    private List<String> options;
    private String correctAnswer;
    @Column(columnDefinition = "TEXT")
    private String explanation;
    private int points;
    @ManyToOne
    @JoinColumn(name = "room_id")
    @JsonIgnore
    private Room room;

    public enum QuestionType {
        MULTIPLE_CHOICE, SHORT_ANSWER
    }
} 