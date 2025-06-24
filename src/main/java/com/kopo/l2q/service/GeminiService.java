package com.kopo.l2q.service;

import com.kopo.l2q.entity.Question;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class GeminiService {
    
    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    
    @Value("${gemini.api.key}")
    private String apiKey;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    
    public List<Question> generateQuestionsFromPDF(MultipartFile pdf, List<String> questionTypes, 
                                                   int questionCount, String difficulty, int timeLimit) {
        logger.info("=== Gemini API로 문제 생성 시작 ===");
        logger.info("PDF 파일명: {}", pdf.getOriginalFilename());
        logger.info("PDF 파일 크기: {} bytes", pdf.getSize());
        logger.info("요청된 문제 유형: {}", questionTypes);
        logger.info("요청된 문제 개수: {}", questionCount);
        logger.info("요청된 난이도: {}", difficulty);
        logger.info("요청된 제한시간: {}초", timeLimit);
        
        try {
            // PDF 내용을 텍스트로 변환
            String pdfContent = extractTextFromPDF(pdf);
            logger.info("PDF 텍스트 추출 완료. 길이: {} 문자", pdfContent.length());
            logger.debug("PDF 내용 (처음 500자): {}", pdfContent.substring(0, Math.min(500, pdfContent.length())));
            
            // Gemini API에 전송할 프롬프트 생성
            String prompt = createPrompt(questionTypes, questionCount, difficulty, pdfContent);
            logger.info("프롬프트 생성 완료. 길이: {} 문자", prompt.length());
            logger.debug("생성된 프롬프트: {}", prompt);
            
            logger.info("Gemini API 요청 시작...");
            
            // Gemini API 호출
            String generatedContent = callGeminiAPI(prompt);
            
            logger.info("Gemini API 응답 받음");
            logger.info("생성된 응답 길이: {} 문자", generatedContent.length());
            logger.info("생성된 응답 내용:");
            logger.info("==================================================");
            logger.info(generatedContent);
            logger.info("==================================================");
            
            // 응답을 파싱하여 문제 목록 생성
            List<Question> questions = parseQuestionsFromResponse(generatedContent, questionCount);
            
            logger.info("문제 생성 완료: {}개", questions.size());
            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                logger.info("문제 {}: ID={}, 질문={}, 선택지={}, 정답={}", 
                    i+1, q.getId(), q.getQuestion(), q.getOptions(), q.getCorrectAnswer());
            }
            
            return questions;
        } catch (Exception e) {
            logger.error("Gemini API 호출 또는 파싱 중 오류 발생: {}", e.getMessage(), e);
            logger.info("Mock 데이터로 폴백");
            return generateMockQuestions(questionCount);
        }
    }
    
    private String callGeminiAPI(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> contents = new HashMap<>();
            List<Map<String, Object>> partsList = new ArrayList<>();
            
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            partsList.add(part);
            
            contents.put("parts", partsList);
            requestBody.put("contents", Arrays.asList(contents));
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            String url = GEMINI_API_URL + "?key=" + apiKey;
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                    List<Map<String, Object>> responseParts = (List<Map<String, Object>>) content.get("parts");
                    if (responseParts != null && !responseParts.isEmpty()) {
                        return (String) responseParts.get(0).get("text");
                    }
                }
            }
            
            throw new RuntimeException("Gemini API 응답 파싱 실패");
            
        } catch (Exception e) {
            logger.error("Gemini API 호출 실패: {}", e.getMessage());
            throw e;
        }
    }
    
    private String extractTextFromPDF(MultipartFile pdf) {
        logger.info("PDF 텍스트 추출 시작 (실제 PDFBox 사용)");
        try (PDDocument document = PDDocument.load(pdf.getInputStream())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            logger.info("PDF 텍스트 추출 완료. 추출 길이: {}자", text.length());
            return text;
        } catch (IOException e) {
            logger.error("PDF 텍스트 추출 실패: {}", e.getMessage(), e);
            return "PDF 텍스트 추출에 실패했습니다.";
        }
    }
    
    private String createPrompt(List<String> questionTypes, int questionCount, String difficulty, String pdfContent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음 PDF 내용을 바탕으로 ").append(questionCount).append("개의 문제를 생성해주세요.\n\n");
        prompt.append("PDF 내용:\n").append(pdfContent).append("\n\n");
        
        prompt.append("요구사항:\n");
        prompt.append("- 문제 유형: ");
        if (questionTypes.contains("multiple-choice")) {
            prompt.append("객관식 (4개 선택지)");
        }
        if (questionTypes.contains("short-answer")) {
            prompt.append(", 단답식");
        }
        prompt.append("\n");
        
        prompt.append("- 난이도: ");
        switch (difficulty) {
            case "easy":
                prompt.append("기본적인 개념과 용어를 묻는 쉬운 문제");
                break;
            case "medium":
                prompt.append("개념을 이해하고 적용할 수 있는 중간 난이도 문제");
                break;
            case "hard":
                prompt.append("심화 사고와 분석이 필요한 어려운 문제");
                break;
        }
        prompt.append("\n");
        
        prompt.append("- 각 문제는 다음 형식으로 작성해주세요:\n");
        prompt.append("문제1: [문제 내용]\n");
        prompt.append("유형: [객관식/단답식]\n");
        if (questionTypes.contains("multiple-choice")) {
            prompt.append("선택지: A) [선택지1] B) [선택지2] C) [선택지3] D) [선택지4]\n");
        }
        prompt.append("정답: [정답]\n");
        prompt.append("설명: [정답 설명]\n");
        prompt.append("점수: [점수]\n\n");
        
        prompt.append("PDF 내용을 바탕으로 학습자가 핵심 개념을 이해했는지 확인할 수 있는 문제를 만들어주세요.");
        
        return prompt.toString();
    }
    
    private List<Question> parseQuestionsFromResponse(String response, int expectedCount) {
        logger.info("Gemini 응답 파싱 시작");
        logger.debug("응답 내용: {}", response);
        List<Question> questions = new ArrayList<>();
        
        try {
            // 간단한 파싱 로직 (실제로는 더 정교한 파싱 필요)
            String[] lines = response.split("\n");
            Question currentQuestion = null;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("문제") || line.startsWith("Q")) {
                    if (currentQuestion != null) {
                        questions.add(currentQuestion);
                    }
                    currentQuestion = new Question();
                    currentQuestion.setId("gemini-" + UUID.randomUUID().toString().substring(0, 8));
                    // 기본값은 객관식으로 설정
                    currentQuestion.setType(Question.QuestionType.MULTIPLE_CHOICE);
                    currentQuestion.setQuestion(line.substring(line.indexOf(":") + 1).trim());
                    currentQuestion.setPoints(1);
                } else if (line.startsWith("유형:") && currentQuestion != null) {
                    // 문제 유형 파싱
                    String typeText = line.substring(line.indexOf(":") + 1).trim().toLowerCase();
                    if (typeText.contains("단답") || typeText.contains("short")) {
                        currentQuestion.setType(Question.QuestionType.SHORT_ANSWER);
                    } else {
                        currentQuestion.setType(Question.QuestionType.MULTIPLE_CHOICE);
                    }
                } else if (line.startsWith("선택지:") && currentQuestion != null) {
                    // 객관식일 때만 선택지 파싱
                    if (currentQuestion.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
                        String optionsText = line.substring(line.indexOf(":") + 1).trim();
                        List<String> options = parseOptions(optionsText);
                        currentQuestion.setOptions(options);
                    }
                } else if (line.startsWith("정답:") && currentQuestion != null) {
                    currentQuestion.setCorrectAnswer(line.substring(line.indexOf(":") + 1).trim());
                } else if (line.startsWith("설명:") && currentQuestion != null) {
                    currentQuestion.setExplanation(line.substring(line.indexOf(":") + 1).trim());
                }
            }
            
            if (currentQuestion != null) {
                questions.add(currentQuestion);
            }
            
            // 생성된 문제가 부족하면 Mock 문제로 보충
            while (questions.size() < expectedCount) {
                questions.add(generateMockQuestion(questions.size() + 1));
            }
            
            // 문제 수를 요청된 개수로 제한
            if (questions.size() > expectedCount) {
                questions = questions.subList(0, expectedCount);
            }
            
        } catch (Exception e) {
            logger.error("응답 파싱 중 오류: {}", e.getMessage());
            // 파싱 실패 시 Mock 데이터 반환
            return generateMockQuestions(expectedCount);
        }
        
        logger.info("파싱 완료: {}개 문제 생성", questions.size());
        return questions;
    }
    
    private List<String> parseOptions(String optionsText) {
        List<String> options = new ArrayList<>();
        logger.info("선택지 파싱 시작: '{}'", optionsText);
        
        try {
            // "A) [선택지1] B) [선택지2] C) [선택지3] D) [선택지4]" 형식 파싱
            String[] parts = optionsText.split("(?=[A-D]\\s*\\))");
            logger.info("분할된 부분들: {}", Arrays.toString(parts));
            
            for (String part : parts) {
                if (part.trim().isEmpty()) {
                    logger.debug("빈 부분 건너뜀");
                    continue;
                }
                // "A) " 부분 제거
                String option = part.replaceAll("^[A-D]\\s*\\)\\s*", "").trim();
                if (!option.isEmpty()) {
                    options.add(option);
                    logger.info("추출된 선택지: '{}'", option);
                } else {
                    logger.warn("빈 선택지 발견: '{}'", part);
                }
            }
            
            logger.info("최종 선택지 개수: {}", options.size());
            
        } catch (Exception e) {
            logger.error("선택지 파싱 실패: {}", e.getMessage(), e);
        }
        
        // 파싱 실패 시 기본 선택지 사용
        if (options.isEmpty()) {
            logger.warn("선택지 파싱 실패, 기본값 사용");
            options = Arrays.asList("첫 번째 선택지", "두 번째 선택지", "세 번째 선택지", "네 번째 선택지");
        }
        
        return options;
    }
    
    private List<Question> generateMockQuestions(int count) {
        logger.info("Mock 문제 {}개 생성", count);
        List<Question> questions = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            questions.add(generateMockQuestion(i + 1));
        }
        
        return questions;
    }
    
    private Question generateMockQuestion(int number) {
        Question question = new Question();
        question.setId("mock-" + number);
        
        // 홀수 번호는 객관식, 짝수 번호는 단답식으로 생성
        if (number % 2 == 1) {
            question.setType(Question.QuestionType.MULTIPLE_CHOICE);
            question.setQuestion("Mock 객관식 문제 " + number + ": 이것은 테스트용 객관식 문제입니다. 다음 중 올바른 답은 무엇일까요?");
            
            List<String> options = Arrays.asList(
                "첫 번째 선택지입니다",
                "두 번째 선택지입니다", 
                "세 번째 선택지입니다",
                "네 번째 선택지입니다"
            );
            question.setOptions(options);
            question.setCorrectAnswer("첫 번째 선택지입니다");
            question.setExplanation("Mock 객관식 문제 " + number + "의 정답 설명입니다. 이 문제는 테스트 목적으로 생성되었습니다.");
            
            logger.info("Mock 객관식 문제 {} 생성 - 선택지: {}", number, options);
        } else {
            question.setType(Question.QuestionType.SHORT_ANSWER);
            question.setQuestion("Mock 단답식 문제 " + number + ": 이것은 테스트용 단답식 문제입니다. 정답을 입력하세요.");
            // 단답식은 선택지 없음
            question.setCorrectAnswer("정답입니다");
            question.setExplanation("Mock 단답식 문제 " + number + "의 정답 설명입니다. 이 문제는 테스트 목적으로 생성되었습니다.");
            
            logger.info("Mock 단답식 문제 {} 생성", number);
        }
        
        question.setPoints(1);
        return question;
    }
} 