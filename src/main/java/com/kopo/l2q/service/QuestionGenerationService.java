package com.kopo.l2q.service;

import com.kopo.l2q.entity.Question;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionGenerationService {
    public List<Question> generateQuestions(MultipartFile pdf, List<String> questionTypes, int questionCount, String difficulty, int timeLimit) {
        // TODO: 실제 LLM 연동 구현 필요. 현재는 Mock 데이터 반환
        return generateMockQuestions(questionCount);
    }
    private List<Question> generateMockQuestions(int count) {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Question question = new Question();
            question.setId("mock-" + (i + 1));
            question.setType(Question.QuestionType.MULTIPLE_CHOICE);
            question.setQuestion("임시 문제 " + (i + 1) + ": 쿼터 초과 시 표시되는 더미 문항입니다.");
            question.setOptions(Arrays.asList("A", "B", "C", "D"));
            question.setCorrectAnswer("A");
            question.setExplanation("실제 AI 문제가 아니라 쿼터 초과 안내용 더미입니다.");
            question.setPoints(1);
            questions.add(question);
        }
        return questions;
    }
    public String generateRoomId() {
        return UUID.randomUUID().toString();
    }
} 