package com.kopo.l2q.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CustomWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomWebSocketHandler.class);
    
    @Autowired
    private RoomService roomService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket 연결됨: {}", session.getId());
        sessions.put(session.getId(), session);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            
            logger.info("메시지 수신: type={}, data={}", type, data);
            
            switch (type) {
                case "join":
                    handleJoin(session, data);
                    break;
                case "chat":
                    handleChat(session, data);
                    break;
                case "leave":
                    handleLeave(session, data);
                    break;
                default:
                    logger.warn("알 수 없는 메시지 타입: {}", type);
            }
        } catch (Exception e) {
            logger.error("메시지 처리 중 오류: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket 연결 해제됨: {}", session.getId());
        sessions.remove(session.getId());
        
        // 세션에서 방 정보 제거
        String roomId = sessionToRoom.remove(session.getId());
        if (roomId != null) {
            // 참가자 목록 업데이트
            List<Participant> participants = roomService.getRoomParticipants(roomId);
            broadcastToRoom(roomId, Map.of(
                "type", "participants-update",
                "participants", participants
            ));
        }
    }
    
    private void handleJoin(WebSocketSession session, Map<String, Object> data) throws Exception {
        String roomId = (String) data.get("roomId");
        String userId = (String) data.get("userId");
        String userName = (String) data.get("userName");
        
        logger.info("방 입장: roomId={}, userId={}, userName={}", roomId, userId, userName);
        
        // 세션과 방 연결
        sessionToRoom.put(session.getId(), roomId);
        
        // 참가자 추가
        roomService.addParticipant(roomId, userId, userName, session.getId());
        
        // 참가자 목록 업데이트
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        
        // 시스템 메시지 전송
        broadcastToRoom(roomId, Map.of(
            "type", "system",
            "message", userName + "님이 입장했습니다.",
            "timestamp", System.currentTimeMillis()
        ));
        
        // 참가자 목록 업데이트 메시지 전송
        broadcastToRoom(roomId, Map.of(
            "type", "participants-update",
            "participants", participants
        ));
    }
    
    private void handleChat(WebSocketSession session, Map<String, Object> data) throws Exception {
        String roomId = (String) data.get("roomId");
        String userId = (String) data.get("userId");
        String userName = (String) data.get("userName");
        String message = (String) data.get("message");
        
        logger.info("채팅 메시지: roomId={}, userName={}, message={}", roomId, userName, message);
        
        // 채팅 메시지 전송
        broadcastToRoom(roomId, Map.of(
            "type", "chat",
            "userId", userId,
            "userName", userName,
            "message", message,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    private void handleLeave(WebSocketSession session, Map<String, Object> data) throws Exception {
        String roomId = (String) data.get("roomId");
        String userId = (String) data.get("userId");
        String userName = (String) data.get("userName");
        
        logger.info("방 퇴장: roomId={}, userId={}, userName={}", roomId, userId, userName);
        
        // 참가자 제거
        roomService.removeParticipant(roomId, userId);
        
        // 참가자 목록 업데이트
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        
        // 시스템 메시지 전송
        broadcastToRoom(roomId, Map.of(
            "type", "system",
            "message", userName + "님이 퇴장했습니다.",
            "timestamp", System.currentTimeMillis()
        ));
        
        // 참가자 목록 업데이트 메시지 전송
        broadcastToRoom(roomId, Map.of(
            "type", "participants-update",
            "participants", participants
        ));
    }
    
    private void broadcastToRoom(String roomId, Map<String, Object> message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(messageJson);
            
            sessions.values().stream()
                .filter(session -> roomId.equals(sessionToRoom.get(session.getId())))
                .forEach(session -> {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(textMessage);
                        }
                    } catch (Exception e) {
                        logger.error("메시지 전송 실패: {}", e.getMessage());
                    }
                });
        } catch (Exception e) {
            logger.error("메시지 직렬화 실패: {}", e.getMessage());
        }
    }
    
    // 외부에서 호출할 수 있는 브로드캐스트 메서드
    public void broadcastQuestionChange(String roomId, int currentQuestion, boolean isLastQuestion, int timeLimit) {
        logger.info("문제 변경 브로드캐스트: roomId={}, currentQuestion={}, isLastQuestion={}", roomId, currentQuestion, isLastQuestion);
        broadcastToRoom(roomId, Map.of(
            "type", "question-change",
            "currentQuestion", currentQuestion,
            "isLastQuestion", isLastQuestion,
            "timeLimit", timeLimit
        ));
    }
    
    public void broadcastQuizFinished(String roomId) {
        logger.info("퀴즈 종료 브로드캐스트: roomId={}", roomId);
        broadcastToRoom(roomId, Map.of(
            "type", "quiz-finished"
        ));
    }
    
    public void broadcastGameStarted(String roomId, int currentQuestion, int totalQuestions, int timeLimit) {
        logger.info("게임 시작 브로드캐스트: roomId={}, currentQuestion={}, totalQuestions={}, timeLimit={}", 
                   roomId, currentQuestion, totalQuestions, timeLimit);
        broadcastToRoom(roomId, Map.of(
            "type", "game-started",
            "currentQuestion", currentQuestion,
            "totalQuestions", totalQuestions,
            "timeLimit", timeLimit,
            "isLastQuestion", currentQuestion >= totalQuestions - 1
        ));
    }
    
    public void broadcastAnswerSubmitted(String roomId, String userId, String userName) {
        logger.info("답안 제출 브로드캐스트: roomId={}, userId={}, userName={}", roomId, userId, userName);
        broadcastToRoom(roomId, Map.of(
            "type", "answer-submitted",
            "userId", userId,
            "userName", userName,
            "timestamp", System.currentTimeMillis()
        ));
    }
} 