package com.kopo.l2q.dto;

import lombok.Data;

@Data
public class GenerateQuestionsResponse {
    private String roomId;
    private String inviteCode;
    private int questionsCount;
} 