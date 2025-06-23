package com.kopo.l2q.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Data
public class GenerateQuestionsRequest {
    private MultipartFile pdf;
    private List<String> questionTypes;
    private int questionCount;
    private String difficulty;
    private int timeLimit;
} 