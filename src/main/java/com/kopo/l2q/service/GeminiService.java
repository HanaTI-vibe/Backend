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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

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
            // API 요청을 위해 텍스트 길이 제한
            if (text.length() > 30000) {
                text = text.substring(0, 30000);
                logger.warn("PDF 내용이 너무 길어 30000자로 제한되었습니다.");
            }
            logger.info("PDF 텍스트 추출 완료. 추출 길이: {}자", text.length());
            return text;
        } catch (IOException e) {
            logger.error("PDF 텍스트 추출 실패: {}", e.getMessage(), e);
            return "PDF 텍스트 추출에 실패했습니다.";
        }
    }
    
    private String createPrompt(List<String> questionTypes, int questionCount, String difficulty, String pdfContent) {
        String questionTypeString = String.join(", ", questionTypes).replace("multiple-choice", "객관식").replace("short-answer", "단답식");
        String difficultyString = "";
        switch (difficulty) {
            case "easy":
                difficultyString = "쉬움";
                break;
            case "medium":
                difficultyString = "중간";
                break;
            case "hard":
                difficultyString = "어려움";
                break;
        }

        return String.format(
            "너는 이제부터 문제 출제 전문가야. 다음 PDF 내용을 기반으로, 아래 요구사항에 맞춰 학습 퀴즈를 생성해줘." +
            "반드시 JSON 형식으로 응답해야 하며, 다른 설명은 포함하지 마." +
            "PDF 내용: \"%s\"\n\n" +
            "요구사항:\n" +
            "- 문제 수: %d개\n" +
            "- 문제 유형: %s\n" +
            "- 난이도: %s\n" +
            "- 응답 형식: 반드시 다음 JSON 구조를 따라야 하며, 객관식 문제는 반드시 4개의 선택지를 생성해야 함.\n" +
            "{\n" +
            "  \"questions\": [\n" +
            "    {\n" +
            "      \"type\": \"객관식 또는 단답식\",\n" +
            "      \"question\": \"문제 내용\",\n" +
            "      \"options\": [\"선택지 A\", \"선택지 B\", \"선택지 C\", \"선택지 D\"],\n" +
            "      \"correctAnswer\": \"정답 내용\",\n" +
            "      \"explanation\": \"정답에 대한 상세 설명\",\n" +
            "      \"points\": 1\n" +
            "    }\n" +
            "  ]\n" +
            "}",
            pdfContent.substring(0, Math.min(pdfContent.length(), 30000)), // PDF 내용 길이 제한
            questionCount,
            questionTypeString,
            difficultyString
        );
    }
    
    private List<Question> parseQuestionsFromResponse(String response, int expectedCount) {
        logger.info("Gemini 응답 파싱 시작");
        List<Question> questions = new ArrayList<>();

        try {
            // 응답에서 JSON 부분만 추출 (마크다운 코드 블록 제거)
            String jsonResponse = response.replaceAll("```json", "").replaceAll("```", "").trim();
            
            // ObjectMapper를 사용하여 JSON 파싱
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            JsonNode questionsNode = rootNode.path("questions");

            if (questionsNode.isArray()) {
                for (JsonNode questionNode : questionsNode) {
                    Question question = new Question();
                    question.setId("gemini-" + UUID.randomUUID().toString().substring(0, 8));

                    String typeStr = questionNode.path("type").asText("").toLowerCase();
                    if (typeStr.contains("객관식") || typeStr.contains("multiple")) {
                        question.setType(Question.QuestionType.MULTIPLE_CHOICE);
                    } else {
                        question.setType(Question.QuestionType.SHORT_ANSWER);
                    }

                    question.setQuestion(questionNode.path("question").asText());
                    String correctAnswer = questionNode.path("correctAnswer").asText();
                    question.setExplanation(questionNode.path("explanation").asText());
                    question.setPoints(questionNode.path("points").asInt(1));

                    if (question.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
                        // 옵션 텍스트에서 "A.", "B)" 같은 마커 제거
                        List<String> options = new ArrayList<>();
                        JsonNode optionsNode = questionNode.path("options");
                        if (optionsNode.isArray()) {
                            for (JsonNode optionNode : optionsNode) {
                                String optionText = optionNode.asText();
                                String cleanedOption = optionText.replaceAll("^[A-Da-d][.)]\\s*", "").trim();
                                options.add(cleanedOption);
                            }
                        }
                        question.setOptions(options);

                        // 정답 텍스트가 어떤 옵션과 일치하는지 찾아 인덱스를 저장
                        String cleanedCorrectAnswer = correctAnswer.replaceAll("^[A-Da-d][.)]\\s*", "").trim();
                        int correctIndex = -1;
                        for (int i = 0; i < options.size(); i++) {
                            // 1. 완전 일치 (대소문자 무시)
                            if (options.get(i).equalsIgnoreCase(cleanedCorrectAnswer)) {
                                correctIndex = i;
                                break;
                            }
                            // 2. 정답이 보기에 포함되는 경우
                            if (cleanedCorrectAnswer.contains(options.get(i))) {
                                correctIndex = i;
                                break;
                            }
                            // 3. 보기가 정답에 포함되는 경우
                            if (options.get(i).contains(cleanedCorrectAnswer)) {
                                correctIndex = i;
                            }
                        }
                        
                        // 정답을 인덱스(문자열)로 저장
                        question.setCorrectAnswer(String.valueOf(correctIndex));
                    } else {
                        question.setCorrectAnswer(correctAnswer);
                    }
                    questions.add(question);
                }
            }

            // 생성된 문제가 부족하면 Mock 문제로 보충
            while (questions.size() < expectedCount) {
                logger.warn("생성된 문제가 요청된 수({})보다 적어 Mock 문제로 보충합니다. (현재: {})", expectedCount, questions.size());
                questions.add(generateMockQuestion(questions.size() + 1));
            }

            // 문제 수를 요청된 개수로 제한
            if (questions.size() > expectedCount) {
                questions = questions.subList(0, expectedCount);
            }

        } catch (Exception e) {
            logger.error("응답 파싱 중 심각한 오류 발생: {}. Mock 데이터로 대체합니다.", e.getMessage());
            return generateMockQuestions(expectedCount);
        }
        
        // 파싱 결과가 비어있으면 Mock 데이터 반환
        if (questions.isEmpty()) {
            logger.warn("파싱 후 생성된 문제가 없습니다. Mock 데이터로 대체합니다.");
            return generateMockQuestions(expectedCount);
        }

        logger.info("파싱 완료: {}개 문제 생성", questions.size());
        return questions;
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