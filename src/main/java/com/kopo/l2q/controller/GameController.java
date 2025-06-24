package com.kopo.l2q.controller;

import com.kopo.l2q.config.CustomWebSocketHandler;
import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Room;
import com.kopo.l2q.dto.RoomResponse;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")
@Tag(name = "Game", description = "게임 진행 및 방 관리 API")
public class GameController {
    
    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    
    @Autowired
    private RoomService roomService;
    
    @Autowired
    private CustomWebSocketHandler webSocketHandler;
    
    private static final ConcurrentHashMap<String, Boolean> roomLogPrinted = new ConcurrentHashMap<>();
    
    @PostMapping("/join-room")
    @Operation(summary = "방 참가", description = "생성된 방에 사용자가 참가합니다.")
    @ApiResponse(responseCode = "200", description = "방 참가 성공", content = @Content(schema = @Schema(implementation = JoinRoomResponse.class)))
    @ApiResponse(responseCode = "404", description = "방을 찾을 수 없음")
    public ResponseEntity<JoinRoomResponse> joinRoom(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "방 참가 정보", required = true, content = @Content(schema = @Schema(implementation = JoinRequest.class)))
            @RequestBody JoinRequest request) {
        logger.info("=== 방 참가 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        roomService.addParticipant(request.roomId, request.userId, request.userName, "rest-" + request.userId);
        
        Room room = roomService.getRoom(request.roomId);
        boolean isHost = roomService.isHost(request.roomId, request.userId);
        
        JoinRoomResponse response = new JoinRoomResponse();
        response.status = "joined";
        response.participants = roomService.getRoomParticipants(request.roomId);
        response.isHost = isHost;
        response.roomStatus = room.getStatus().name();
        
        logger.info("방 참가 성공: {} (사용자: {}, 방장: {})", request.roomId, request.userName, isHost);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/start-game")
    @Operation(summary = "게임 시작", description = "방장이 게임을 시작합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "게임 시작 성공", content = @Content(schema = @Schema(implementation = GameActionResponse.class))),
        @ApiResponse(responseCode = "400", description = "게임 시작 실패"),
        @ApiResponse(responseCode = "403", description = "방장이 아님")
    })
    public ResponseEntity<GameActionResponse> startGame(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "게임 시작 정보", required = true, content = @Content(schema = @Schema(implementation = StartGameRequest.class)))
            @RequestBody StartGameRequest request) {
        logger.info("=== 게임 시작 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        boolean success = roomService.startGame(request.roomId, request.userId);
        
        if (success) {
            Room room = roomService.getRoom(request.roomId);
            List<Question> questions = room.getQuestions();
            Question firstQuestion = questions.get(0);
            
            GameActionResponse response = new GameActionResponse();
            response.status = "started";
            response.currentQuestion = 0;
            response.question = firstQuestion;
            response.totalQuestions = questions.size();
            response.timeLimit = room.getTimeLimit();
            response.participants = roomService.getRoomParticipants(request.roomId);
            
            // WebSocket으로 모든 클라이언트에게 게임 시작 알림
            webSocketHandler.broadcastGameStarted(request.roomId, 0, questions.size(), room.getTimeLimit());
            
            logger.info("게임 시작 성공: 룸 {}", request.roomId);
            return ResponseEntity.ok(response);
        } else {
            logger.warn("게임 시작 실패: 룸 {}, 사용자 {}", request.roomId, request.userId);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/submit-answer")
    @Operation(summary = "답변 제출", description = "현재 문제에 대한 사용자의 답변을 제출합니다.")
    @ApiResponse(responseCode = "200", description = "답변 제출 성공", content = @Content(schema = @Schema(implementation = SubmitAnswerResponse.class)))
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "답변 제출 정보", required = true, content = @Content(schema = @Schema(implementation = SubmitAnswerRequest.class)))
            @RequestBody SubmitAnswerRequest request) {
        logger.info("=== 답안 제출 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        Room room = roomService.getRoom(request.roomId);
        if (room == null) {
            logger.warn("룸을 찾을 수 없음: {}", request.roomId);
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
            isCorrect = request.answer.equals(currentQuestion.getCorrectAnswer());
            points = isCorrect ? currentQuestion.getPoints() : 0;
        } else if (currentQuestion.getType() == Question.QuestionType.SHORT_ANSWER) {
            // 단답형 정답 처리
            if (request.answer != null && currentQuestion.getCorrectAnswer() != null) {
                // 대소문자 구분 없이 비교, 앞뒤 공백 제거
                String userAnswer = request.answer.trim().toLowerCase();
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
        
        roomService.updateParticipantScore(request.roomId, request.userId, points);
        
        // WebSocket으로 모든 클라이언트에게 답안 제출 알림
        // 사용자 이름을 가져오기 위해 참가자 목록에서 찾기
        List<Participant> participants = roomService.getRoomParticipants(request.roomId);
        String userName = participants.stream()
            .filter(p -> p.getId().equals(request.userId))
            .findFirst()
            .map(Participant::getName)
            .orElse("Unknown");
        
        webSocketHandler.broadcastAnswerSubmitted(request.roomId, request.userId, userName);
        
        SubmitAnswerResponse result = new SubmitAnswerResponse();
        result.isCorrect = isCorrect;
        result.points = points;
        result.explanation = currentQuestion.getExplanation();
        result.participants = roomService.getRoomParticipants(request.roomId);
        
        logger.info("답안 제출 완료: 사용자 {}, 정답여부 {}, 점수 {}", request.userId, isCorrect, points);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/next-question")
    @Operation(summary = "다음 문제로 이동", description = "방장이 다음 문제로 넘어갑니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "다음 문제 이동 성공 또는 퀴즈 종료", content = {
            @Content(mediaType = "application/json", schema = @Schema(oneOf = {GameActionResponse.class, QuizFinishedResponse.class}))
        }),
        @ApiResponse(responseCode = "404", description = "방을 찾을 수 없음")
    })
    public ResponseEntity<?> nextQuestion(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "다음 문제 이동 정보", required = true, content = @Content(schema = @Schema(implementation = NextQuestionRequest.class)))
            @RequestBody NextQuestionRequest request) {
        logger.info("=== 다음 문제 API 호출 ===");
        logger.info("요청 데이터: {}", request);
        
        Room room = roomService.getRoom(request.roomId);
        if (room == null) {
            logger.warn("룸을 찾을 수 없음: {}", request.roomId);
            return ResponseEntity.notFound().build();
        }
        
        if (room.getCurrentQuestion() < room.getQuestions().size() - 1) {
            roomService.nextQuestion(request.roomId);
            
            List<Question> questions = room.getQuestions();
            Question nextQuestion = questions.get(room.getCurrentQuestion());
            
            GameActionResponse response = new GameActionResponse();
            response.currentQuestion = room.getCurrentQuestion();
            response.question = nextQuestion;
            response.totalQuestions = questions.size();
            response.isLastQuestion = room.getCurrentQuestion() == questions.size() - 1;
            
            // WebSocket으로 모든 클라이언트에게 문제 변경 알림
            webSocketHandler.broadcastQuestionChange(request.roomId, room.getCurrentQuestion(), 
                room.getCurrentQuestion() == questions.size() - 1, room.getTimeLimit());
            
            logger.info("다음 문제로 이동: {} -> {}", room.getCurrentQuestion() - 1, room.getCurrentQuestion());
            return ResponseEntity.ok(response);
        } else {
            room.setStatus(Room.RoomStatus.FINISHED);
            
            QuizFinishedResponse response = new QuizFinishedResponse();
            response.status = "finished";
            response.finalScores = roomService.getRoomParticipants(request.roomId);
            
            // WebSocket으로 퀴즈 종료 알림
            webSocketHandler.broadcastQuizFinished(request.roomId);
            
            logger.info("퀴즈 종료: {}", request.roomId);
            return ResponseEntity.ok(response);
        }
    }
    
    @GetMapping("/room/{roomId}/current-question")
    @Operation(summary = "현재 문제 정보 조회", description = "진행중인 게임의 현재 문제 정보를 가져옵니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 문제 정보 조회 성공", content = @Content(schema = @Schema(implementation = GameActionResponse.class))),
            @ApiResponse(responseCode = "404", description = "방 또는 문제를 찾을 수 없음")
    })
    public ResponseEntity<GameActionResponse> getCurrentQuestion(@PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null || room.getStatus() != Room.RoomStatus.ACTIVE) {
            return ResponseEntity.notFound().build();
        }
        
        List<Question> questions = room.getQuestions();
        int currentQuestionIndex = room.getCurrentQuestion();
        
        if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) {
            return ResponseEntity.notFound().build();
        }
        
        Question currentQuestion = questions.get(currentQuestionIndex);
        
        GameActionResponse response = new GameActionResponse();
        response.currentQuestion = currentQuestionIndex;
        response.question = currentQuestion;
        response.totalQuestions = questions.size();
        response.isLastQuestion = currentQuestionIndex == questions.size() - 1;
        response.participants = roomService.getRoomParticipants(roomId);
        response.timeLimit = room.getTimeLimit();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/room/{roomId}/participants")
    @Operation(summary = "참가자 목록 조회", description = "방에 참여하고 있는 모든 참가자의 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "참가자 목록 조회 성공")
    @ApiResponse(responseCode = "404", description = "방을 찾을 수 없음")
    public ResponseEntity<List<Participant>> getParticipants(
            @Parameter(description = "조회할 방의 ID", required = true) @PathVariable String roomId) {
        logger.info("=== 참가자 목록 조회 API 호출 ===");
        logger.info("룸 ID: {}", roomId);
        
        List<Participant> participants = roomService.getRoomParticipants(roomId);
        logger.info("참가자 {}명 조회 완료", participants.size());
        
        return ResponseEntity.ok(participants);
    }
    
    @DeleteMapping("/room/{roomId}/participant/{userId}")
    @Operation(summary = "참가자 강퇴", description = "방장이 특정 참가자를 방에서 내보냅니다.")
    @ApiResponse(responseCode = "200", description = "참가자 강퇴 성공")
    @ApiResponse(responseCode = "404", description = "방 또는 참가자를 찾을 수 없음")
    public ResponseEntity<Void> removeParticipant(
            @Parameter(description = "방의 ID", required = true) @PathVariable String roomId,
            @Parameter(description = "내보낼 참가자의 ID", required = true) @PathVariable String userId) {
        logger.info("=== 참가자 제거 API 호출 ===");
        logger.info("룸 ID: {}, 사용자 ID: {}", roomId, userId);
        
        roomService.removeParticipant(roomId, userId);
        logger.info("참가자 제거 완료: {}", userId);
        
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/room/{roomId}")
    @Operation(summary = "방 정보 조회", description = "특정 방의 현재 상태 및 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "방 정보 조회 성공", content = @Content(schema = @Schema(implementation = RoomResponse.class)))
    @ApiResponse(responseCode = "404", description = "방을 찾을 수 없음")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomId) {
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
        RoomResponse roomData = new RoomResponse(
            room.getId(),
            room.getQuestions(),
            room.getCurrentQuestion(),
            room.getStatus().name().toLowerCase(),
            room.getTimeLimit(),
            room.getInviteCode(),
            roomService.getRoomParticipants(roomId)
        );
        if (firstLog) logger.info("방 정보 조회 완료: {}", roomId);
        return ResponseEntity.ok(roomData);
    }

    // --- DTO for Swagger Schemas ---

    @Schema(name = "JoinRequest", description = "방 참가 요청 데이터")
    private static class JoinRequest {
        @Schema(description = "참가할 방의 ID", example = "c73f9553-3b03-4a10-8cc3-af3690b5dba2", required = true)
        public String roomId;
        @Schema(description = "참가자 고유 ID", example = "user_1715830294", required = true)
        public String userId;
        @Schema(description = "참가자 이름", example = "홍길동", required = true)
        public String userName;
    }

    @Schema(name = "StartGameRequest", description = "게임 시작 요청 데이터")
    private static class StartGameRequest {
        @Schema(description = "시작할 방의 ID", required = true)
        public String roomId;
        @Schema(description = "게임을 시작하는 방장의 사용자 ID", required = true)
        public String userId;
    }

    @Schema(name = "SubmitAnswerRequest", description = "답변 제출 요청 데이터")
    private static class SubmitAnswerRequest {
        @Schema(description = "현재 방의 ID", required = true)
        public String roomId;
        @Schema(description = "답변을 제출하는 사용자의 ID", required = true)
        public String userId;
        @Schema(description = "현재 문제의 ID", required = true)
        public String questionId;
        @Schema(description = "사용자가 제출한 답변 (객관식의 경우 선택지 인덱스)", required = true)
        public String answer;
        @Schema(description = "타임아웃에 의한 자동 제출 여부", defaultValue = "false")
        public boolean isAutoSubmit;
    }

    @Schema(name = "NextQuestionRequest", description = "다음 문제 이동 요청 데이터")
    private static class NextQuestionRequest {
        @Schema(description = "진행중인 방의 ID", required = true)
        public String roomId;
    }

    @Schema(name = "JoinRoomResponse", description = "방 참가 응답 데이터")
    private static class JoinRoomResponse {
        @Schema(description = "참가 상태", example = "joined")
        public String status;
        @Schema(description = "현재 참가자 목록")
        public List<Participant> participants;
        @Schema(description = "방장 여부")
        public boolean isHost;
        @Schema(description = "현재 방 상태", example = "WAITING")
        public String roomStatus;
    }

    @Schema(name = "GameActionResponse", description = "게임 액션 응답 데이터 (게임 시작, 다음 문제 등)")
    private static class GameActionResponse {
        @Schema(description = "API 호출 결과 상태")
        public String status;
        @Schema(description = "현재 문제 번호 (0-based)")
        public int currentQuestion;
        @Schema(description = "현재 문제 정보")
        public Question question;
        @Schema(description = "전체 문제 수")
        public int totalQuestions;
        @Schema(description = "문제당 제한 시간(초)")
        public int timeLimit;
        @Schema(description = "마지막 문제 여부")
        public boolean isLastQuestion;
        @Schema(description = "현재 참가자 목록 (점수 포함)")
        public List<Participant> participants;
    }

    @Schema(name = "SubmitAnswerResponse", description = "답변 제출 결과 데이터")
    private static class SubmitAnswerResponse {
        @Schema(description = "정답 여부")
        public boolean isCorrect;
        @Schema(description = "획득한 점수")
        public int points;
        @Schema(description = "문제에 대한 해설")
        public String explanation;
        @Schema(description = "업데이트된 참가자 목록")
        public List<Participant> participants;
    }

    @Schema(name = "QuizFinishedResponse", description = "퀴즈 종료 응답 데이터")
    private static class QuizFinishedResponse {
        @Schema(description = "최종 상태", example = "finished")
        public String status;
        @Schema(description = "최종 점수를 포함한 참가자 순위")
        public List<Participant> finalScores;
    }
} 