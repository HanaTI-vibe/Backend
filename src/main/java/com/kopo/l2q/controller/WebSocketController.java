package com.kopo.l2q.controller;

import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Room;
import com.kopo.l2q.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.List;
import java.util.Map;

@Controller
public class WebSocketController {
    @Autowired
    private RoomService roomService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/join-room")
    public void joinRoom(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String userId = payload.get("userId");
        String userName = payload.get("userName");
        roomService.addParticipant(roomId, userId, userName, "ws-" + userId);
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/participants", participants);
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
} 