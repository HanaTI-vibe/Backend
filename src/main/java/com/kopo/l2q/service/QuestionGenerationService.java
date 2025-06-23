package com.kopo.l2q.service;

import com.kopo.l2q.entity.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionGenerationService.class);
    
    @Autowired
    private GeminiService geminiService;
    
    public List<Question> generateQuestions(MultipartFile pdf, List<String> questionTypes, int questionCount, String difficulty, int timeLimit) {
        logger.info("=== 문제 생성 서비스 호출 ===");
        logger.info("PDF 파일명: {}", pdf.getOriginalFilename());
        logger.info("요청된 문제 유형: {}", questionTypes);
        logger.info("요청된 문제 개수: {}", questionCount);
        logger.info("요청된 난이도: {}", difficulty);
        logger.info("요청된 제한시간: {}초", timeLimit);
        
        // Gemini API를 사용하여 문제 생성
        logger.info("Gemini API로 문제 생성 시작...");
        List<Question> questions = geminiService.generateQuestionsFromPDF(pdf, questionTypes, questionCount, difficulty, timeLimit);
        logger.info("문제 생성 완료: {}개", questions.size());
        
        return questions;
    }
    
    public String generateRoomId() {
        String roomId = UUID.randomUUID().toString();
        logger.info("룸 ID 생성: {}", roomId);
        return roomId;
    }
} 