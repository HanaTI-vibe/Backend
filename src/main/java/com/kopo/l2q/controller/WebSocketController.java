package com.kopo.l2q.controller;

import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Room;
import com.kopo.l2q.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.List;
import java.util.Map;

@Controller
public class WebSocketController {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    @Autowired
    private RoomService roomService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/room/{roomId}/join")
    @SendTo("/topic/room/{roomId}")
    public Map<String, Object> joinRoom(@DestinationVariable String roomId, 
                                       @Payload Map<String, String> joinRequest,
                                       SimpMessageHeaderAccessor headerAccessor) {
        logger.info("=== WebSocket 방 입장 ===");
        logger.info("룸 ID: {}, 사용자: {}", roomId, joinRequest.get("userName"));
        
        String userId = joinRequest.get("userId");
        String userName = joinRequest.get("userName");
        
        // 참가자 추가
        roomService.addParticipant(roomId, userId, userName, headerAccessor.getSessionId());
        
        // 참가자 목록 업데이트
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        
        // 시스템 메시지 생성
        Map<String, Object> systemMessage = Map.of(
            "type", "system",
            "message", userName + "님이 입장했습니다.",
            "timestamp", System.currentTimeMillis()
        );
        
        // 참가자 목록 업데이트 메시지
        Map<String, Object> participantsUpdate = Map.of(
            "type", "participants-update",
            "participants", participants
        );
        
        // 두 메시지를 순차적으로 전송
        messagingTemplate.convertAndSend("/topic/room/" + roomId, systemMessage);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, participantsUpdate);
        
        return Map.of(
            "type", "join-success",
            "userId", userId,
            "userName", userName,
            "participants", participants
        );
    }

    @MessageMapping("/room/{roomId}/chat")
    @SendTo("/topic/room/{roomId}")
    public Map<String, Object> sendMessage(@DestinationVariable String roomId, 
                                          @Payload Map<String, Object> chatMessage) {
        logger.info("=== WebSocket 채팅 메시지 ===");
        logger.info("룸 ID: {}, 사용자: {}, 메시지: {}", 
            roomId, chatMessage.get("userName"), chatMessage.get("message"));
        
        return Map.of(
            "type", "chat",
            "userId", chatMessage.get("userId"),
            "userName", chatMessage.get("userName"),
            "message", chatMessage.get("message"),
            "timestamp", System.currentTimeMillis()
        );
    }

    @MessageMapping("/room/{roomId}/leave")
    @SendTo("/topic/room/{roomId}")
    public Map<String, Object> leaveRoom(@DestinationVariable String roomId, 
                                        @Payload Map<String, String> leaveRequest) {
        logger.info("=== WebSocket 방 퇴장 ===");
        logger.info("룸 ID: {}, 사용자: {}", roomId, leaveRequest.get("userName"));
        
        String userId = leaveRequest.get("userId");
        String userName = leaveRequest.get("userName");
        
        // 참가자 제거
        roomService.removeParticipant(roomId, userId);
        
        // 참가자 목록 업데이트
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        
        // 시스템 메시지 생성
        Map<String, Object> systemMessage = Map.of(
            "type", "system",
            "message", userName + "님이 퇴장했습니다.",
            "timestamp", System.currentTimeMillis()
        );
        
        // 참가자 목록 업데이트 메시지
        Map<String, Object> participantsUpdate = Map.of(
            "type", "participants-update",
            "participants", participants
        );
        
        // 두 메시지를 순차적으로 전송
        messagingTemplate.convertAndSend("/topic/room/" + roomId, systemMessage);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, participantsUpdate);
        
        return Map.of(
            "type", "leave-success",
            "userId", userId,
            "userName", userName,
            "participants", participants
        );
    }

    @MessageMapping("/submit-answer")
    public void submitAnswer(@Payload Map<String, Object> payload) {
        String roomId = (String) payload.get("roomId");
        String userId = (String) payload.get("userId");
        String answer = (String) payload.get("answer");
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        List<Question> questions = room.getQuestions();
        if (room.getCurrentQuestion() >= questions.size()) return;
        Question currentQuestion = questions.get(room.getCurrentQuestion());
        boolean isCorrect = answer.equals(currentQuestion.getCorrectAnswer());
        int points = isCorrect ? currentQuestion.getPoints() : 0;
        roomService.updateParticipantScore(roomId, userId, points);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/participants", roomService.getRoomParticipants(roomId));
    }

    @MessageMapping("/next-question")
    public void nextQuestion(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        roomService.nextQuestion(roomId);
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/question", room.getQuestions().get(room.getCurrentQuestion()));
    }

    @MessageMapping("/chat-message")
    public void chatMessage(@Payload Map<String, Object> payload) {
        String roomId = (String) payload.get("roomId");
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", payload);
    }

    // Socket.IO 스타일 HTTP 엔드포인트 추가
    @org.springframework.web.bind.annotation.PostMapping("/api/socket/chat")
    public void socketChat(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String roomId = (String) payload.get("roomId");
        String userId = (String) payload.get("userId");
        String userName = (String) payload.get("userName");
        String message = (String) payload.get("message");
        
        logger.info("Socket.IO 스타일 채팅: 룸={}, 사용자={}, 메시지={}", roomId, userName, message);
        
        Map<String, Object> chatMessage = Map.of(
            "type", "chat",
            "userId", userId,
            "userName", userName,
            "message", message,
            "timestamp", System.currentTimeMillis()
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, chatMessage);
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/socket/join")
    public void socketJoin(@org.springframework.web.bind.annotation.RequestBody Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String userId = payload.get("userId");
        String userName = payload.get("userName");
        
        logger.info("Socket.IO 스타일 입장: 룸={}, 사용자={}", roomId, userName);
        
        // 참가자 추가
        roomService.addParticipant(roomId, userId, userName, "http-" + userId);
        
        // 참가자 목록 업데이트
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        
        // 시스템 메시지 생성
        Map<String, Object> systemMessage = Map.of(
            "type", "system",
            "message", userName + "님이 입장했습니다.",
            "timestamp", System.currentTimeMillis()
        );
        
        // 참가자 목록 업데이트 메시지
        Map<String, Object> participantsUpdate = Map.of(
            "type", "participants-update",
            "participants", participants
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, systemMessage);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, participantsUpdate);
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/socket/leave")
    public void socketLeave(@org.springframework.web.bind.annotation.RequestBody Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String userId = payload.get("userId");
        String userName = payload.get("userName");
        
        logger.info("Socket.IO 스타일 퇴장: 룸={}, 사용자={}", roomId, userName);
        
        // 참가자 제거
        roomService.removeParticipant(roomId, userId);
        
        // 참가자 목록 업데이트
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        
        // 시스템 메시지 생성
        Map<String, Object> systemMessage = Map.of(
            "type", "system",
            "message", userName + "님이 퇴장했습니다.",
            "timestamp", System.currentTimeMillis()
        );
        
        // 참가자 목록 업데이트 메시지
        Map<String, Object> participantsUpdate = Map.of(
            "type", "participants-update",
            "participants", participants
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, systemMessage);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, participantsUpdate);
    }

    @org.springframework.web.bind.annotation.GetMapping("/api/socket/messages/{roomId}")
    public java.util.List<Map<String, Object>> getChatMessages(@org.springframework.web.bind.annotation.PathVariable String roomId) {
        // 실제로는 데이터베이스에서 메시지를 가져와야 함
        // 현재는 간단한 테스트용으로 빈 리스트 반환
        logger.info("채팅 메시지 요청: 룸={}", roomId);
        return new java.util.ArrayList<>();
    }
} 