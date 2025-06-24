package com.kopo.l2q.controller;

import com.kopo.l2q.config.CustomWebSocketHandler;
import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Room;
import com.kopo.l2q.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")
public class GameController {
    
    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    
    @Autowired
    private RoomService roomService;
    
    @Autowired
    private CustomWebSocketHandler webSocketHandler;
    
    private static final ConcurrentHashMap<String, Boolean> roomLogPrinted = new ConcurrentHashMap<>();
    
    @PostMapping("/join-room")
    public ResponseEntity<Map<String, Object>> joinRoom(@RequestBody Map<String, String> request) {
        logger.info("=== 방 참가 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        String roomId = request.get("roomId");
        String userId = request.get("userId");
        String userName = request.get("userName");
        
        if (roomId == null || userId == null || userName == null) {
            logger.warn("필수 파라미터 누락");
            return ResponseEntity.badRequest().build();
        }
        
        roomService.addParticipant(roomId, userId, userName, "rest-" + userId);
        
        Room room = roomService.getRoom(roomId);
        boolean isHost = roomService.isHost(roomId, userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "joined");
        response.put("participants", roomService.getRoomParticipants(roomId));
        response.put("isHost", isHost);
        response.put("roomStatus", room.getStatus().name());
        
        logger.info("방 참가 성공: {} (사용자: {}, 방장: {})", roomId, userName, isHost);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/start-game")
    public ResponseEntity<Map<String, Object>> startGame(@RequestBody Map<String, String> request) {
        logger.info("=== 게임 시작 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        String roomId = request.get("roomId");
        String userId = request.get("userId");
        
        if (roomId == null || userId == null) {
            logger.warn("필수 파라미터 누락");
            return ResponseEntity.badRequest().build();
        }
        
        boolean success = roomService.startGame(roomId, userId);
        
        if (success) {
            Room room = roomService.getRoom(roomId);
            List<Question> questions = room.getQuestions();
            Question firstQuestion = questions.get(0);
            
            Map<String, Object> questionData = new HashMap<>();
            questionData.put("id", firstQuestion.getId());
            questionData.put("type", firstQuestion.getType().name().toLowerCase());
            questionData.put("question", firstQuestion.getQuestion());
            questionData.put("options", firstQuestion.getOptions());
            questionData.put("points", firstQuestion.getPoints());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "started");
            response.put("currentQuestion", 0);
            response.put("question", questionData);
            response.put("totalQuestions", questions.size());
            response.put("timeLimit", room.getTimeLimit());
            response.put("participants", roomService.getRoomParticipants(roomId));
            
            logger.info("게임 시작 성공: 룸 {}", roomId);
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "failed");
            response.put("message", "게임을 시작할 수 없습니다.");
            
            logger.warn("게임 시작 실패: 룸 {}, 사용자 {}", roomId, userId);
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/submit-answer")
    public ResponseEntity<Map<String, Object>> submitAnswer(@RequestBody Map<String, Object> request) {
        logger.info("=== 답안 제출 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        String roomId = (String) request.get("roomId");
        String userId = (String) request.get("userId");
        String questionId = (String) request.get("questionId");
        String answer = (String) request.get("answer");
        Boolean isAutoSubmitObj = (Boolean) request.get("isAutoSubmit");
        boolean isAutoSubmit = isAutoSubmitObj != null ? isAutoSubmitObj : false;
        
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            logger.warn("룸을 찾을 수 없음: {}", roomId);
            return ResponseEntity.notFound().build();
        }
        
        List<Question> questions = room.getQuestions();
        if (room.getCurrentQuestion() >= questions.size()) {
            logger.warn("현재 문제 인덱스 오류: {} >= {}", room.getCurrentQuestion(), questions.size());
            return ResponseEntity.badRequest().build();
        }
        
        Question currentQuestion = questions.get(room.getCurrentQuestion());
        boolean isCorrect = false;
        int points = 0;
        
        if (currentQuestion.getType() == Question.QuestionType.MULTIPLE_CHOICE) {
            // 인덱스 기반 정답 비교
            isCorrect = answer.equals(currentQuestion.getCorrectAnswer());
            points = isCorrect ? currentQuestion.getPoints() : 0;
        } else if (currentQuestion.getType() == Question.QuestionType.SHORT_ANSWER) {
            // 단답형 정답 처리
            if (answer != null && currentQuestion.getCorrectAnswer() != null) {
                // 대소문자 구분 없이 비교, 앞뒤 공백 제거
                String userAnswer = answer.trim().toLowerCase();
                String correctAnswer = currentQuestion.getCorrectAnswer().trim().toLowerCase();
                
                // 정확히 일치하는 경우
                if (userAnswer.equals(correctAnswer)) {
                    isCorrect = true;
                    points = currentQuestion.getPoints();
                } 
                // 부분 일치하는 경우 (정답이 사용자 답안에 포함되거나 그 반대)
                else if (userAnswer.contains(correctAnswer) || correctAnswer.contains(userAnswer)) {
                    isCorrect = true;
                    points = (int) (currentQuestion.getPoints() * 0.8); // 80% 점수
                }
                // 완전히 틀린 경우
                else {
                    isCorrect = false;
                    points = 0;
                }
            } else {
                // 답안이 비어있거나 정답이 설정되지 않은 경우
                isCorrect = false;
                points = 0;
            }
        }
        
        roomService.updateParticipantScore(roomId, userId, points);
        
        Map<String, Object> result = new HashMap<>();
        result.put("isCorrect", isCorrect);
        result.put("points", points);
        result.put("explanation", currentQuestion.getExplanation());
        result.put("participants", roomService.getRoomParticipants(roomId));
        
        logger.info("답안 제출 완료: 사용자 {}, 정답여부 {}, 점수 {}", userId, isCorrect, points);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/next-question")
    public ResponseEntity<Map<String, Object>> nextQuestion(@RequestBody Map<String, String> request) {
        logger.info("=== 다음 문제 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        String roomId = request.get("roomId");
        
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            logger.warn("룸을 찾을 수 없음: {}", roomId);
            return ResponseEntity.notFound().build();
        }
        
        if (room.getCurrentQuestion() < room.getQuestions().size() - 1) {
            roomService.nextQuestion(roomId);
            
            List<Question> questions = room.getQuestions();
            Question nextQuestion = questions.get(room.getCurrentQuestion());
            
            Map<String, Object> questionData = new HashMap<>();
            questionData.put("id", nextQuestion.getId());
            questionData.put("type", nextQuestion.getType().name().toLowerCase());
            questionData.put("question", nextQuestion.getQuestion());
            questionData.put("options", nextQuestion.getOptions());
            questionData.put("points", nextQuestion.getPoints());
            
            Map<String, Object> response = new HashMap<>();
            response.put("currentQuestion", room.getCurrentQuestion());
            response.put("question", questionData);
            response.put("totalQuestions", questions.size());
            response.put("isLastQuestion", room.getCurrentQuestion() == questions.size() - 1);
            
            // WebSocket으로 모든 클라이언트에게 문제 변경 알림
            webSocketHandler.broadcastQuestionChange(roomId, room.getCurrentQuestion(), 
                room.getCurrentQuestion() == questions.size() - 1, room.getTimeLimit());
            
            logger.info("다음 문제로 이동: {} -> {}", room.getCurrentQuestion() - 1, room.getCurrentQuestion());
            return ResponseEntity.ok(response);
        } else {
            room.setStatus(Room.RoomStatus.FINISHED);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "finished");
            response.put("finalScores", roomService.getRoomParticipants(roomId));
            
            // WebSocket으로 퀴즈 종료 알림
            webSocketHandler.broadcastQuizFinished(roomId);
            
            logger.info("퀴즈 종료: {}", roomId);
            return ResponseEntity.ok(response);
        }
    }
    

    
    @GetMapping("/room/{roomId}/current-question")
    public ResponseEntity<Map<String, Object>> getCurrentQuestion(@PathVariable String roomId) {
        logger.info("=== 현재 문제 조회 API 호출 ===");
        logger.info("룸 ID: {}", roomId);
        
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            logger.warn("룸을 찾을 수 없음: {}", roomId);
            return ResponseEntity.notFound().build();
        }
        
        List<Question> questions = room.getQuestions();
        if (room.getCurrentQuestion() >= questions.size()) {
            logger.warn("현재 문제 인덱스 오류: {} >= {}", room.getCurrentQuestion(), questions.size());
            return ResponseEntity.badRequest().build();
        }
        
        Question currentQuestion = questions.get(room.getCurrentQuestion());
        
        Map<String, Object> questionData = new HashMap<>();
        questionData.put("id", currentQuestion.getId());
        questionData.put("type", currentQuestion.getType().name().toLowerCase());
        questionData.put("question", currentQuestion.getQuestion());
        questionData.put("options", currentQuestion.getOptions());
        questionData.put("points", currentQuestion.getPoints());
        
        Map<String, Object> response = new HashMap<>();
        response.put("currentQuestion", room.getCurrentQuestion());
        response.put("question", questionData);
        response.put("totalQuestions", questions.size());
        response.put("isLastQuestion", room.getCurrentQuestion() == questions.size() - 1);
        
        logger.info("현재 문제 조회 완료: 문제 {}/{}", room.getCurrentQuestion() + 1, questions.size());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/room/{roomId}/participants")
    public ResponseEntity<List<Participant>> getParticipants(@PathVariable String roomId) {
        logger.info("=== 참가자 목록 조회 API 호출 ===");
        logger.info("룸 ID: {}", roomId);
        
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        logger.info("참가자 {}명 조회 완료", participants.size());
        
        return ResponseEntity.ok(participants);
    }
    
    @DeleteMapping("/room/{roomId}/participant/{userId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable String roomId, @PathVariable String userId) {
        logger.info("=== 참가자 제거 API 호출 ===");
        logger.info("룸 ID: {}, 사용자 ID: {}", roomId, userId);
        
        roomService.removeParticipant(roomId, userId);
        logger.info("참가자 제거 완료: {}", userId);
        
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/room/{roomId}")
    public ResponseEntity<Map<String, Object>> getRoom(@PathVariable String roomId) {
        boolean firstLog = roomLogPrinted.putIfAbsent(roomId, true) == null;
        if (firstLog) {
            logger.info("=== 방 정보 조회 API 호출 ===");
            logger.info("룸 ID: {}", roomId);
        }
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            if (firstLog) logger.warn("룸을 찾을 수 없음: {}", roomId);
            return ResponseEntity.notFound().build();
        }
        if (firstLog) {
            logger.info("룸 정보 조회 성공: {}", roomId);
            logger.info("룸 상태: {}", room.getStatus());
            logger.info("현재 문제: {}/{}", room.getCurrentQuestion(), room.getQuestions().size());
            logger.info("문제 개수: {}", room.getQuestions().size());
            for (int i = 0; i < room.getQuestions().size(); i++) {
                Question q = room.getQuestions().get(i);
                logger.info("문제 {}: ID={}, 질문={}, 선택지={}", i+1, q.getId(), q.getQuestion(), q.getOptions());
            }
        }
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("id", room.getId());
        roomData.put("questions", room.getQuestions());
        roomData.put("currentQuestion", room.getCurrentQuestion());
        roomData.put("status", room.getStatus().name().toLowerCase());
        roomData.put("timeLimit", room.getTimeLimit());
        roomData.put("inviteCode", room.getInviteCode());
        roomData.put("participants", roomService.getRoomParticipants(roomId));
        if (firstLog) logger.info("방 정보 조회 완료: {}", roomId);
        return ResponseEntity.ok(roomData);
    }
} 