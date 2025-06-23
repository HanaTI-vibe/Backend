package com.kopo.l2q.controller;

import com.kopo.l2q.dto.GenerateQuestionsResponse;
import com.kopo.l2q.dto.RoomResponse;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Room;
import com.kopo.l2q.service.QuestionGenerationService;
import com.kopo.l2q.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/generate-questions")
@CrossOrigin(origins = "*")
public class QuestionController {
    @Autowired
    private QuestionGenerationService questionGenerationService;
    @Autowired
    private RoomService roomService;

    @PostMapping
    public ResponseEntity<GenerateQuestionsResponse> generateQuestions(
            @RequestParam("pdf") MultipartFile pdf,
            @RequestParam("questionTypes") String questionTypesJson,
            @RequestParam("questionCount") int questionCount,
            @RequestParam("difficulty") String difficulty,
            @RequestParam("timeLimit") int timeLimit) {
        try {
            List<String> questionTypes = List.of("multiple-choice", "short-answer");
            List<Question> questions = questionGenerationService.generateQuestions(
                pdf, questionTypes, questionCount, difficulty, timeLimit);
            String roomId = questionGenerationService.generateRoomId();
            String inviteCode = roomService.generateInviteCode();
            boolean isMock = questions.get(0).getId().startsWith("mock-");
            for (Question question : questions) {
                question.setRoom(new Room());
                question.getRoom().setId(roomId);
            }
            Room room = roomService.createRoom(roomId, inviteCode, questions, timeLimit, isMock);
            GenerateQuestionsResponse response = new GenerateQuestionsResponse();
            response.setRoomId(roomId);
            response.setInviteCode(inviteCode);
            response.setQuestionsCount(questions.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<RoomResponse> getRoom(@RequestParam("roomId") String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        // RoomResponse 변환 생략(간단화)
        return ResponseEntity.ok(new RoomResponse());
    }
} 