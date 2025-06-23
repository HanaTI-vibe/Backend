package com.kopo.l2q.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoomResponse {
    private String id;
    private String inviteCode;
    private List<QuestionDto> questions;
    private List<ParticipantDto> participants;
    private String status;
    private int currentQuestion;
    private int timeLimit;
    private LocalDateTime createdAt;
    private boolean isMock;

    @Data
    public static class QuestionDto {
        private String id;
        private String type;
        private String question;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private int points;
    }

    @Data
    public static class ParticipantDto {
        private String id;
        private String name;
        private int score;
        private boolean isReady;
    }
} 