package com.kopo.l2q.service;

import com.kopo.l2q.entity.Room;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Participant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);
    
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Participant>> participants = new ConcurrentHashMap<>();

    public String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        String inviteCode = result.toString();
        logger.info("초대코드 생성: {}", inviteCode);
        return inviteCode;
    }

    public Room createRoom(String roomId, String inviteCode, List<Question> questions, int timeLimit, boolean isMock) {
        logger.info("=== 룸 생성 시작 ===");
        logger.info("룸 ID: {}", roomId);
        logger.info("초대코드: {}", inviteCode);
        logger.info("문제 개수: {}", questions.size());
        logger.info("제한시간: {}초", timeLimit);
        logger.info("Mock 모드: {}", isMock);
        
        Room room = new Room();
        room.setId(roomId);
        room.setInviteCode(inviteCode);
        room.setQuestions(questions);
        room.setTimeLimit(timeLimit);
        room.setCreatedAt(LocalDateTime.now());
        room.setMock(isMock);
        room.setStatus(Room.RoomStatus.WAITING);
        room.setCurrentQuestion(0);
        
        rooms.put(roomId, room);
        participants.put(roomId, new ConcurrentHashMap<>());
        
        logger.info("룸 생성 완료: {}", roomId);
        return room;
    }

    public Room getRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            logger.debug("룸 조회 성공: {}", roomId);
        } else {
            logger.debug("룸 조회 실패: {} (존재하지 않음)", roomId);
        }
        return room;
    }

    public Room getRoomByInviteCode(String inviteCode) {
        logger.debug("초대코드로 룸 검색: {}", inviteCode);
        Room room = rooms.values().stream()
                .filter(r -> r.getInviteCode().equals(inviteCode.toUpperCase()))
                .findFirst()
                .orElse(null);
        
        if (room != null) {
            logger.debug("초대코드로 룸 찾음: {} -> {}", inviteCode, room.getId());
        } else {
            logger.debug("초대코드로 룸 찾지 못함: {}", inviteCode);
        }
        return room;
    }

    public void addParticipant(String roomId, String userId, String userName, String socketId) {
        logger.info("=== 참가자 추가 ===");
        logger.info("룸 ID: {}", roomId);
        logger.info("사용자 ID: {}", userId);
        logger.info("사용자명: {}", userName);
        logger.info("소켓 ID: {}", socketId);
        
        Map<String, Participant> roomParticipants = participants.get(roomId);
        if (roomParticipants != null) {
            Participant participant = new Participant();
            participant.setId(userId);
            participant.setName(userName);
            participant.setSocketId(socketId);
            participant.setScore(0);
            participant.setReady(false);
            
            roomParticipants.put(userId, participant);
            logger.info("참가자 추가 완료: {} (룸: {})", userName, roomId);
        } else {
            logger.warn("룸을 찾을 수 없어 참가자 추가 실패: {}", roomId);
        }
    }

    public void removeParticipant(String roomId, String userId) {
        logger.info("참가자 제거: 룸 {}에서 사용자 {}", roomId, userId);
        Map<String, Participant> roomParticipants = participants.get(roomId);
        if (roomParticipants != null) {
            roomParticipants.remove(userId);
            logger.info("참가자 제거 완료: {}", userId);
        }
    }

    public List<Participant> getRoomParticipants(String roomId) {
        Map<String, Participant> roomParticipants = participants.get(roomId);
        List<Participant> participantList = roomParticipants != null ? new ArrayList<>(roomParticipants.values()) : new ArrayList<>();
        logger.debug("룸 {} 참가자 목록 조회: {}명", roomId, participantList.size());
        return participantList;
    }

    public void updateParticipantScore(String roomId, String userId, int points) {
        logger.info("점수 업데이트: 룸 {}, 사용자 {}, 점수 {}", roomId, userId, points);
        Map<String, Participant> roomParticipants = participants.get(roomId);
        if (roomParticipants != null) {
            Participant participant = roomParticipants.get(userId);
            if (participant != null) {
                int oldScore = participant.getScore();
                participant.setScore(participant.getScore() + points);
                logger.info("점수 업데이트 완료: {} -> {} (변화: {})", oldScore, participant.getScore(), points);
            } else {
                logger.warn("참가자를 찾을 수 없음: {}", userId);
            }
        }
    }

    public void nextQuestion(String roomId) {
        logger.info("다음 문제로 이동: 룸 {}", roomId);
        Room room = rooms.get(roomId);
        if (room != null && room.getCurrentQuestion() < room.getQuestions().size() - 1) {
            int oldQuestion = room.getCurrentQuestion();
            room.setCurrentQuestion(room.getCurrentQuestion() + 1);
            logger.info("문제 이동: {} -> {}", oldQuestion, room.getCurrentQuestion());
        } else if (room != null) {
            room.setStatus(Room.RoomStatus.FINISHED);
            logger.info("퀴즈 종료: 룸 {}", roomId);
        }
    }
} 