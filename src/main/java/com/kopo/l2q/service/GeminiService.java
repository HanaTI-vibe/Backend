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
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
    
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
        logger.info("PDF 텍스트 추출 시작");
        try (PDDocument document = PDDocument.load(pdf.getInputStream())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            // 더 정확한 텍스트 추출을 위한 설정
            pdfStripper.setSortByPosition(true);
            pdfStripper.setStartPage(1);
            pdfStripper.setEndPage(document.getNumberOfPages());
            
            String text = pdfStripper.getText(document);
            logger.info("PDF 텍스트 추출 완료. 추출 길이: {}자", text.length());
            
            // 텍스트가 너무 짧으면 에러 처리
            if (text.trim().length() < 50) {
                logger.error("PDF에서 추출된 텍스트가 너무 짧습니다: {}", text);
                throw new IOException("PDF에서 충분한 텍스트를 추출할 수 없습니다.");
            }
            
            logger.debug("PDF 내용 (처음 1000자): {}", text.substring(0, Math.min(1000, text.length())));
            return text;
        } catch (IOException e) {
            logger.error("PDF 텍스트 추출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("PDF 텍스트 추출에 실패했습니다: " + e.getMessage(), e);
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
        
        prompt.append("- 반드시 다음 형식으로 작성해주세요 (각 문제마다 구분선 포함):\n");
        prompt.append("---\n");
        prompt.append("문제1: [문제 내용]\n");
        prompt.append("선택지: A) [선택지1] B) [선택지2] C) [선택지3] D) [선택지4]\n");
        prompt.append("정답: [정답]\n");
        prompt.append("설명: [정답 설명]\n");
        prompt.append("점수: [점수]\n");
        prompt.append("---\n");
        prompt.append("문제2: [문제 내용]\n");
        prompt.append("선택지: A) [선택지1] B) [선택지2] C) [선택지3] D) [선택지4]\n");
        prompt.append("정답: [정답]\n");
        prompt.append("설명: [정답 설명]\n");
        prompt.append("점수: [점수]\n");
        prompt.append("---\n");
        prompt.append("...\n\n");
        
        prompt.append("중요한 규칙:\n");
        prompt.append("1. 각 문제는 반드시 '---' 구분선으로 구분해주세요\n");
        prompt.append("2. 문제 번호는 '문제1:', '문제2:' 형식으로 작성해주세요\n");
        prompt.append("3. 선택지는 'A) [내용] B) [내용] C) [내용] D) [내용]' 형식으로 작성해주세요\n");
        prompt.append("4. 정답은 선택지 중 하나의 정확한 내용을 작성해주세요\n");
        prompt.append("5. 설명은 왜 그 답이 정답인지 설명해주세요\n");
        prompt.append("6. 점수는 1-5 사이의 숫자로 작성해주세요\n");
        prompt.append("7. PDF 내용을 바탕으로 학습자가 핵심 개념을 이해했는지 확인할 수 있는 문제를 만들어주세요\n");
        prompt.append("8. 정확히 ").append(questionCount).append("개의 문제를 생성해주세요\n");
        
        return prompt.toString();
    }
    
    private List<Question> parseQuestionsFromResponse(String response, int expectedCount) {
        logger.info("Gemini 응답 파싱 시작");
        logger.debug("응답 내용: {}", response);
        List<Question> questions = new ArrayList<>();
        
        try {
            // 응답이 비어있거나 너무 짧으면 에러
            if (response == null || response.trim().isEmpty()) {
                throw new RuntimeException("Gemini API 응답이 비어있습니다.");
            }
            
            if (response.trim().length() < 100) {
                logger.warn("Gemini API 응답이 너무 짧습니다: {}", response);
            }
            
            // 더 정교한 파싱 로직
            String[] lines = response.split("\n");
            Question currentQuestion = null;
            StringBuilder currentQuestionText = new StringBuilder();
            
            for (String line : lines) {
                line = line.trim();
                
                // 새로운 문제 시작
                if (line.matches("^문제\\d*\\s*[:：]\\s*.+") || line.matches("^Q\\d*\\s*[:：]\\s*.+")) {
                    // 이전 문제 저장
                    if (currentQuestion != null && !currentQuestionText.toString().trim().isEmpty()) {
                        currentQuestion.setQuestion(currentQuestionText.toString().trim());
                        questions.add(currentQuestion);
                    }
                    
                    // 새 문제 시작
                    currentQuestion = new Question();
                    currentQuestion.setId("gemini-" + UUID.randomUUID().toString().substring(0, 8));
                    currentQuestion.setType(Question.QuestionType.MULTIPLE_CHOICE);
                    currentQuestion.setPoints(1);
                    
                    // 문제 텍스트 초기화
                    currentQuestionText = new StringBuilder();
                    String questionText = line.substring(line.indexOf(":") + 1).trim();
                    if (line.indexOf("：") != -1) {
                        questionText = line.substring(line.indexOf("：") + 1).trim();
                    }
                    currentQuestionText.append(questionText);
                    
                } else if (line.startsWith("선택지") && currentQuestion != null) {
                    // 선택지 파싱
                    String optionsText = line.substring(line.indexOf(":") + 1).trim();
                    if (line.indexOf("：") != -1) {
                        optionsText = line.substring(line.indexOf("：") + 1).trim();
                    }
                    List<String> options = parseOptions(optionsText);
                    currentQuestion.setOptions(options);
                    
                } else if (line.startsWith("정답") && currentQuestion != null) {
                    // 정답 파싱
                    String answer = line.substring(line.indexOf(":") + 1).trim();
                    if (line.indexOf("：") != -1) {
                        answer = line.substring(line.indexOf("：") + 1).trim();
                    }
                    currentQuestion.setCorrectAnswer(answer);
                    
                } else if (line.startsWith("설명") && currentQuestion != null) {
                    // 설명 파싱
                    String explanation = line.substring(line.indexOf(":") + 1).trim();
                    if (line.indexOf("：") != -1) {
                        explanation = line.substring(line.indexOf("：") + 1).trim();
                    }
                    currentQuestion.setExplanation(explanation);
                    
                } else if (line.startsWith("점수") && currentQuestion != null) {
                    // 점수 파싱
                    String pointsText = line.substring(line.indexOf(":") + 1).trim();
                    if (line.indexOf("：") != -1) {
                        pointsText = line.substring(line.indexOf("：") + 1).trim();
                    }
                    try {
                        int points = Integer.parseInt(pointsText);
                        currentQuestion.setPoints(points);
                    } catch (NumberFormatException e) {
                        logger.warn("점수 파싱 실패, 기본값 1 사용: {}", pointsText);
                    }
                    
                } else if (currentQuestion != null && !line.isEmpty() && !line.startsWith("---")) {
                    // 문제 텍스트 계속
                    currentQuestionText.append(" ").append(line);
                }
            }
            
            // 마지막 문제 저장
            if (currentQuestion != null && !currentQuestionText.toString().trim().isEmpty()) {
                currentQuestion.setQuestion(currentQuestionText.toString().trim());
                questions.add(currentQuestion);
            }
            
            // 파싱된 문제 검증
            for (Question q : questions) {
                if (q.getQuestion() == null || q.getQuestion().trim().isEmpty()) {
                    logger.warn("문제 텍스트가 비어있는 문제 발견: {}", q.getId());
                }
                if (q.getOptions() == null || q.getOptions().isEmpty()) {
                    logger.warn("선택지가 없는 문제 발견: {}", q.getId());
                    q.setOptions(Arrays.asList("선택지 A", "선택지 B", "선택지 C", "선택지 D"));
                }
                if (q.getCorrectAnswer() == null || q.getCorrectAnswer().trim().isEmpty()) {
                    logger.warn("정답이 없는 문제 발견: {}", q.getId());
                    q.setCorrectAnswer("선택지 A");
                }
            }
            
            logger.info("파싱 완료: {}개 문제 생성", questions.size());
            
            // 생성된 문제가 부족하면 Mock 문제로 보충
            while (questions.size() < expectedCount) {
                logger.info("문제가 부족하여 Mock 문제 {}개 추가", expectedCount - questions.size());
                questions.add(generateMockQuestion(questions.size() + 1));
            }
            
            // 문제 수를 요청된 개수로 제한
            if (questions.size() > expectedCount) {
                questions = questions.subList(0, expectedCount);
            }
            
        } catch (Exception e) {
            logger.error("응답 파싱 중 오류: {}", e.getMessage(), e);
            logger.info("Mock 데이터로 폴백");
            return generateMockQuestions(expectedCount);
        }
        
        return questions;
    }
    
    private List<String> parseOptions(String optionsText) {
        List<String> options = new ArrayList<>();
        try {
            logger.debug("선택지 파싱 시작: {}", optionsText);
            
            // 다양한 형식 지원
            // 1. "A) [선택지1] B) [선택지2] C) [선택지3] D) [선택지4]" 형식
            // 2. "A. [선택지1] B. [선택지2] C. [선택지3] D. [선택지4]" 형식
            // 3. "① [선택지1] ② [선택지2] ③ [선택지3] ④ [선택지4]" 형식
            
            // 정규식으로 선택지 분리
            String[] parts = optionsText.split("(?=[A-D][\\)\\.]\\s*|[\u2460-\u2463]\\s*)");
            
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                
                // 선택지 번호 제거
                String option = part.replaceAll("^[A-D][\\)\\.]\\s*|^[\u2460-\u2463]\\s*", "").trim();
                
                if (!option.isEmpty()) {
                    options.add(option);
                    logger.debug("선택지 추가: {}", option);
                }
            }
            
            // 파싱 실패 시 다른 방법 시도
            if (options.isEmpty()) {
                // 쉼표나 다른 구분자로 분리
                String[] commaParts = optionsText.split("[,，]");
                for (String part : commaParts) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        options.add(part);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("선택지 파싱 실패: {}", e.getMessage());
        }
        
        // 파싱 실패 시 기본 선택지 사용
        if (options.isEmpty()) {
            logger.warn("선택지 파싱 실패, 기본값 사용");
            options = Arrays.asList("첫 번째 선택지", "두 번째 선택지", "세 번째 선택지", "네 번째 선택지");
        }
        
        // 선택지가 4개가 되도록 조정
        while (options.size() < 4) {
            options.add("추가 선택지 " + (options.size() + 1));
        }
        
        if (options.size() > 4) {
            options = options.subList(0, 4);
        }
        
        logger.debug("최종 선택지: {}", options);
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
        question.setType(Question.QuestionType.MULTIPLE_CHOICE);
        question.setQuestion("Mock 문제 " + number + ": 이것은 테스트용 문제입니다. 다음 중 올바른 답은 무엇일까요?");
        question.setOptions(Arrays.asList(
            "첫 번째 선택지입니다",
            "두 번째 선택지입니다", 
            "세 번째 선택지입니다",
            "네 번째 선택지입니다"
        ));
        question.setCorrectAnswer("첫 번째 선택지입니다");
        question.setExplanation("Mock 문제 " + number + "의 정답 설명입니다. 이 문제는 테스트 목적으로 생성되었습니다.");
        question.setPoints(1);
        return question;
    }
} 