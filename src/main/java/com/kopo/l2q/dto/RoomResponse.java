package com.kopo.l2q.dto;

import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.entity.Question;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    public RoomResponse() {}

    public RoomResponse(String id, List<Question> questions, int currentQuestion, String status, int timeLimit, String inviteCode, List<Participant> participants) {
        this.id = id;
        this.questions = questions.stream().map(QuestionDto::new).collect(Collectors.toList());
        this.currentQuestion = currentQuestion;
        this.status = status;
        this.timeLimit = timeLimit;
        this.inviteCode = inviteCode;
        this.participants = participants.stream().map(ParticipantDto::new).collect(Collectors.toList());
    }

    @Data
    public static class QuestionDto {
        private String id;
        private String type;
        private String question;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private int points;

        public QuestionDto(Question question) {
            this.id = question.getId();
            this.type = question.getType().name();
            this.question = question.getQuestion();
            this.options = question.getOptions();
            this.correctAnswer = question.getCorrectAnswer();
            this.explanation = question.getExplanation();
            this.points = question.getPoints();
        }
    }

    @Data
    public static class ParticipantDto {
        private String id;
        private String name;
        private int score;
        private boolean isReady;

        public ParticipantDto(Participant participant) {
            this.id = participant.getId();
            this.name = participant.getName();
            this.score = participant.getScore();
            this.isReady = participant.isReady();
        }
    }
} 