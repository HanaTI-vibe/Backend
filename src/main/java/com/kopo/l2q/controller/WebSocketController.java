package com.kopo.l2q.controller;

import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Room;
import com.kopo.l2q.entity.ChatMessage;
import com.kopo.l2q.repository.ChatMessageRepository;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.time.LocalDateTime;

@RestController
public class WebSocketController {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    @Autowired
    private RoomService roomService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ChatMessageRepository chatMessageRepository;

    // 채팅 메시지 저장소 (실제로는 데이터베이스 사용 권장)
    private final Map<String, List<Map<String, Object>>> chatMessages = new ConcurrentHashMap<>();

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
    @PostMapping("/api/socket/chat")
    public void socketChat(@RequestBody Map<String, Object> payload) {
        String roomId = (String) payload.get("roomId");
        String userId = (String) payload.get("userId");
        String userName = (String) payload.get("userName");
        String message = (String) payload.get("message");
        
        logger.info("Socket.IO 스타일 채팅: 룸={}, 사용자={}, 메시지={}", roomId, userName, message);
        
        // DB에 메시지 저장
        ChatMessage chatMessageEntity = new ChatMessage();
        chatMessageEntity.setRoomId(roomId);
        chatMessageEntity.setUserId(userId);
        chatMessageEntity.setUserName(userName);
        chatMessageEntity.setMessage(message);
        chatMessageEntity.setType(ChatMessage.MessageType.MESSAGE);
        chatMessageEntity.setTimestamp(LocalDateTime.now());
        
        chatMessageRepository.save(chatMessageEntity);
        logger.info("DB에 메시지 저장됨. ID: {}", chatMessageEntity.getId());
        
        Map<String, Object> chatMessage = Map.of(
            "type", "chat",
            "userId", userId,
            "userName", userName,
            "message", message,
            "timestamp", System.currentTimeMillis()
        );
        
        // 메시지를 저장소에 저장 (메모리)
        chatMessages.computeIfAbsent(roomId, k -> new ArrayList<>()).add(chatMessage);
        logger.info("메시지 저장됨. 룸 {}의 총 메시지 수: {}", roomId, chatMessages.get(roomId).size());
        
        // STOMP WebSocket으로 브로드캐스트
        messagingTemplate.convertAndSend("/topic/room/" + roomId, chatMessage);
        logger.info("STOMP 브로드캐스트 완료: /topic/room/{}", roomId);
    }

    @PostMapping("/api/socket/join")
    public void socketJoin(@RequestBody Map<String, String> payload) {
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
        
        // 시스템 메시지를 DB에 저장
        ChatMessage systemMessageEntity = new ChatMessage();
        systemMessageEntity.setRoomId(roomId);
        systemMessageEntity.setUserId("system");
        systemMessageEntity.setUserName("시스템");
        systemMessageEntity.setMessage(userName + "님이 입장했습니다.");
        systemMessageEntity.setType(ChatMessage.MessageType.SYSTEM);
        systemMessageEntity.setTimestamp(LocalDateTime.now());
        
        chatMessageRepository.save(systemMessageEntity);
        logger.info("DB에 시스템 메시지 저장됨. ID: {}", systemMessageEntity.getId());
        
        // 시스템 메시지 저장 (메모리)
        chatMessages.computeIfAbsent(roomId, k -> new ArrayList<>()).add(systemMessage);
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, systemMessage);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, participantsUpdate);
    }

    @PostMapping("/api/socket/leave")
    public void socketLeave(@RequestBody Map<String, String> payload) {
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
        
        // 시스템 메시지를 DB에 저장
        ChatMessage systemMessageEntity = new ChatMessage();
        systemMessageEntity.setRoomId(roomId);
        systemMessageEntity.setUserId("system");
        systemMessageEntity.setUserName("시스템");
        systemMessageEntity.setMessage(userName + "님이 퇴장했습니다.");
        systemMessageEntity.setType(ChatMessage.MessageType.SYSTEM);
        systemMessageEntity.setTimestamp(LocalDateTime.now());
        
        chatMessageRepository.save(systemMessageEntity);
        logger.info("DB에 시스템 메시지 저장됨. ID: {}", systemMessageEntity.getId());
        
        // 시스템 메시지 저장 (메모리)
        chatMessages.computeIfAbsent(roomId, k -> new ArrayList<>()).add(systemMessage);
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId, systemMessage);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, participantsUpdate);
    }

    @GetMapping("/api/socket/messages/{roomId}")
    public List<Map<String, Object>> getChatMessages(@PathVariable String roomId) {
        logger.info("채팅 메시지 요청: 룸={}", roomId);
        
        // DB에서 메시지 조회
        List<ChatMessage> dbMessages = chatMessageRepository.findByRoomIdOrderByTimestampAsc(roomId);
        logger.info("DB에서 조회된 메시지 수: {}", dbMessages.size());
        
        // Map 형태로 변환
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : dbMessages) {
            Map<String, Object> messageMap = Map.of(
                "type", msg.getType() == ChatMessage.MessageType.MESSAGE ? "chat" : "system",
                "userId", msg.getUserId(),
                "userName", msg.getUserName(),
                "message", msg.getMessage(),
                "timestamp", msg.getTimestamp().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            );
            messages.add(messageMap);
        }
        
        logger.info("반환할 메시지 수: {}", messages.size());
        return messages;
    }
} 