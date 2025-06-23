package com.kopo.l2q.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.l2q.dto.GenerateQuestionsResponse;
import com.kopo.l2q.dto.RoomResponse;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Room;
import com.kopo.l2q.service.QuestionGenerationService;
import com.kopo.l2q.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/generate-questions")
@CrossOrigin(origins = "*")
public class QuestionController {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionController.class);
    
    @Autowired
    private QuestionGenerationService questionGenerationService;
    
    @Autowired
    private RoomService roomService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public ResponseEntity<GenerateQuestionsResponse> generateQuestions(
            @RequestParam("pdf") MultipartFile pdf,
            @RequestParam("questionTypes") String questionTypesJson,
            @RequestParam("questionCount") int questionCount,
            @RequestParam("difficulty") String difficulty,
            @RequestParam("timeLimit") int timeLimit) {
        
        logger.info("=== 문제 생성 API 호출 ===");
        logger.info("PDF 파일명: {}", pdf.getOriginalFilename());
        logger.info("PDF 파일 크기: {} bytes", pdf.getSize());
        logger.info("문제 유형 JSON: {}", questionTypesJson);
        logger.info("문제 개수: {}", questionCount);
        logger.info("난이도: {}", difficulty);
        logger.info("제한시간: {}초", timeLimit);
        
        try {
            // 프론트엔드에서 전송한 문제 타입 JSON을 파싱
            List<String> questionTypes;
            try {
                questionTypes = objectMapper.readValue(questionTypesJson, new TypeReference<List<String>>() {});
                logger.info("파싱된 문제 유형: {}", questionTypes);
            } catch (Exception e) {
                logger.error("문제 유형 JSON 파싱 실패: {}", e.getMessage());
                // 파싱 실패 시 기본값으로 객관식 사용
                questionTypes = List.of("multiple-choice");
                logger.info("기본 문제 유형으로 설정: {}", questionTypes);
            }
            
            // 문제 유형 검증
            if (questionTypes.isEmpty()) {
                logger.warn("문제 유형이 비어있음. 기본값으로 객관식 설정");
                questionTypes = List.of("multiple-choice");
            }
            
            logger.info("최종 사용할 문제 유형: {}", questionTypes);
            logger.info("문제 생성 시작...");
            
            List<Question> questions = questionGenerationService.generateQuestions(
                pdf, questionTypes, questionCount, difficulty, timeLimit);
            logger.info("문제 생성 완료: {}개", questions.size());
            
            String roomId = questionGenerationService.generateRoomId();
            String inviteCode = roomService.generateInviteCode();
            boolean isMock = questions.get(0).getId().startsWith("mock-");
            
            logger.info("룸 ID 생성: {}", roomId);
            logger.info("초대코드 생성: {}", inviteCode);
            logger.info("Mock 모드: {}", isMock);
            
            for (Question question : questions) {
                question.setRoom(new Room());
                question.getRoom().setId(roomId);
            }
            
            Room room = roomService.createRoom(roomId, inviteCode, questions, timeLimit, isMock);
            logger.info("룸 생성 완료: {}", room.getId());
            
            GenerateQuestionsResponse response = new GenerateQuestionsResponse();
            response.setRoomId(roomId);
            response.setInviteCode(inviteCode);
            response.setQuestionsCount(questions.size());
            
            logger.info("=== 문제 생성 API 응답 ===");
            logger.info("룸 ID: {}", response.getRoomId());
            logger.info("초대코드: {}", response.getInviteCode());
            logger.info("문제 개수: {}", response.getQuestionsCount());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("문제 생성 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<RoomResponse> getRoom(@RequestParam("roomId") String roomId) {
        logger.info("=== 룸 정보 조회 API 호출 ===");
        logger.info("요청된 룸 ID: {}", roomId);
        
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            logger.warn("룸을 찾을 수 없음: {}", roomId);
            return ResponseEntity.notFound().build();
        }
        
        logger.info("룸 정보 조회 성공: {}", roomId);
        logger.info("룸 상태: {}", room.getStatus());
        logger.info("현재 문제: {}/{}", room.getCurrentQuestion(), room.getQuestions().size());
        
        // RoomResponse 변환 생략(간단화)
        return ResponseEntity.ok(new RoomResponse());
    }
} 