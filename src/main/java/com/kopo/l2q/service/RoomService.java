package com.kopo.l2q.service;

import com.kopo.l2q.entity.Room;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Participant;
import com.kopo.l2q.repository.RoomRepository;
import com.kopo.l2q.repository.QuestionRepository;
import com.kopo.l2q.repository.ParticipantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.Random;

@Service
@Transactional
public class RoomService {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);
    
    @Autowired
    private RoomRepository roomRepository;
    
    @Autowired
    private QuestionRepository questionRepository;
    
    @Autowired
    private ParticipantRepository participantRepository;
    
    // 메모리 캐시 (성능 향상을 위해)
    private final Map<String, Room> roomCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Participant>> participantCache = new ConcurrentHashMap<>();

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
        
        // DB에 저장 시도
        try {
            roomRepository.save(room);
            logger.info("DB에 룸 저장됨: {}", roomId);
            
            // 문제들을 DB에 저장
            for (Question question : questions) {
                question.setRoom(room);
                questionRepository.save(question);
            }
            logger.info("DB에 문제 {}개 저장됨", questions.size());
        } catch (Exception e) {
            logger.warn("룸 DB 저장 실패, 메모리에만 저장: {}", e.getMessage());
        }
        
        // 캐시에 저장
        roomCache.put(roomId, room);
        participantCache.put(roomId, new ConcurrentHashMap<>());
        
        logger.info("룸 생성 완료: {}", roomId);
        return room;
    }

    public Room getRoom(String roomId) {
        Room room = roomCache.get(roomId);
        if (room != null) {
            logger.debug("룸 조회 성공: {}", roomId);
        } else {
            logger.debug("룸 조회 실패: {} (존재하지 않음)", roomId);
        }
        return room;
    }

    public Room getRoomByInviteCode(String inviteCode) {
        logger.debug("초대코드로 룸 검색: {}", inviteCode);
        
        // 디버깅: 현재 캐시에 있는 모든 방들 출력
        logger.info("=== 현재 캐시에 있는 방들 ===");
        logger.info("총 방 개수: {}", roomCache.size());
        for (Map.Entry<String, Room> entry : roomCache.entrySet()) {
            Room room = entry.getValue();
            logger.info("룸 ID: {}, 초대코드: {}, 상태: {}", 
                room.getId(), room.getInviteCode(), room.getStatus());
        }
        logger.info("=== 캐시 확인 완료 ===");
        
        Room room = roomCache.values().stream()
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
        
        Room room = roomCache.get(roomId);
        if (room == null) {
            logger.warn("룸을 찾을 수 없어 참가자 추가 실패: {}", roomId);
            return;
        }
        
        Map<String, Participant> roomParticipants = participantCache.get(roomId);
        if (roomParticipants == null) {
            roomParticipants = new ConcurrentHashMap<>();
            participantCache.put(roomId, roomParticipants);
        }
        
        // 이미 참가한 사용자인지 확인
        if (roomParticipants.containsKey(userId)) {
            logger.info("이미 참가한 사용자: {} (룸: {})", userName, roomId);
            return;
        }
        
        Participant participant = new Participant();
        participant.setId(userId);
        participant.setName(userName);
        participant.setSocketId(socketId);
        participant.setScore(0);
        participant.setReady(false);
        participant.setRoom(room);
        
        // 첫 번째 참가자를 방장으로 설정
        boolean isFirstParticipant = roomParticipants.isEmpty();
        if (isFirstParticipant || room.getHostUserId() == null || room.getHostUserId().isEmpty()) {
            room.setHostUserId(userId);
            try {
                roomRepository.save(room); // DB에 방장 정보 저장
                logger.info("방장 설정 및 DB 저장: {} (룸: {})", userName, roomId);
            } catch (Exception e) {
                logger.warn("방장 정보 DB 저장 실패, 메모리에만 저장: {}", e.getMessage());
            }
        }
        
        // DB에 저장 시도
        try {
            participantRepository.save(participant);
            logger.info("DB에 참가자 저장됨: {}", userId);
        } catch (Exception e) {
            logger.warn("참가자 DB 저장 실패, 메모리에만 저장: {}", e.getMessage());
        }
        
        roomParticipants.put(userId, participant);
        logger.info("참가자 추가 완료: {} (룸: {}, 총 참가자: {}명)", userName, roomId, roomParticipants.size());
    }

    public void removeParticipant(String roomId, String userId) {
        logger.info("참가자 제거: 룸 {}에서 사용자 {}", roomId, userId);
        Map<String, Participant> roomParticipants = participantCache.get(roomId);
        if (roomParticipants != null) {
            roomParticipants.remove(userId);
            
            // DB에서 삭제 시도
            try {
                Room room = roomCache.get(roomId);
                if (room != null) {
                    participantRepository.deleteByRoomAndId(room, userId);
                    logger.info("DB에서 참가자 삭제됨: {}", userId);
                }
            } catch (Exception e) {
                logger.warn("참가자 DB 삭제 실패, 캐시에서만 제거: {}", e.getMessage());
            }
            
            logger.info("참가자 제거 완료: {}", userId);
        }
    }

    public List<Participant> getRoomParticipants(String roomId) {
        Map<String, Participant> roomParticipants = participantCache.get(roomId);
        List<Participant> participantList = roomParticipants != null ? new ArrayList<>(roomParticipants.values()) : new ArrayList<>();
        
        // DB에서도 조회 시도 (실패하면 캐시만 사용)
        try {
            Room room = roomCache.get(roomId);
            if (room != null) {
                List<Participant> dbParticipants = participantRepository.findByRoom(room);
                logger.debug("DB에서 조회된 참가자 수: {}", dbParticipants.size());
            }
        } catch (Exception e) {
            logger.warn("DB에서 참가자 조회 실패, 캐시 데이터 사용: {}", e.getMessage());
        }
        
        logger.debug("룸 {} 참가자 목록 조회: {}명", roomId, participantList.size());
        return participantList;
    }

    public void updateParticipantScore(String roomId, String userId, int points) {
        logger.info("점수 업데이트: 룸 {}, 사용자 {}, 점수 {}", roomId, userId, points);
        Map<String, Participant> roomParticipants = participantCache.get(roomId);
        if (roomParticipants != null) {
            Participant participant = roomParticipants.get(userId);
            if (participant != null) {
                int oldScore = participant.getScore();
                participant.setScore(participant.getScore() + points);
                
                // DB에 저장 시도
                try {
                    participantRepository.save(participant);
                    logger.info("DB에 점수 업데이트 저장됨: {}", userId);
                } catch (Exception e) {
                    logger.warn("점수 DB 저장 실패, 메모리에만 저장: {}", e.getMessage());
                }
                
                logger.info("점수 업데이트 완료: {} -> {} (변화: {})", oldScore, participant.getScore(), points);
            } else {
                logger.warn("참가자를 찾을 수 없음: {}", userId);
            }
        }
    }

    public void nextQuestion(String roomId) {
        logger.info("다음 문제로 이동: 룸 {}", roomId);
        Room room = roomCache.get(roomId);
        if (room != null && room.getCurrentQuestion() < room.getQuestions().size() - 1) {
            int oldQuestion = room.getCurrentQuestion();
            room.setCurrentQuestion(room.getCurrentQuestion() + 1);
            
            // DB에 상태 저장
            roomRepository.save(room);
            
            logger.info("문제 이동: {} -> {} (DB 저장됨)", oldQuestion, room.getCurrentQuestion());
        } else if (room != null) {
            room.setStatus(Room.RoomStatus.FINISHED);
            
            // DB에 상태 저장
            roomRepository.save(room);
            
            logger.info("퀴즈 종료: 룸 {} (DB 저장됨)", roomId);
        }
    }
    
    public boolean isHost(String roomId, String userId) {
        Room room = roomCache.get(roomId);
        return room != null && userId.equals(room.getHostUserId());
    }
    
    public boolean startGame(String roomId, String userId) {
        logger.info("게임 시작 요청: 룸 {}, 사용자 {}", roomId, userId);
        Room room = roomCache.get(roomId);
        
        if (room == null) {
            logger.warn("룸을 찾을 수 없음: {}", roomId);
            return false;
        }
        
        if (!userId.equals(room.getHostUserId())) {
            logger.warn("방장이 아닌 사용자가 게임 시작 시도: {} (방장: {})", userId, room.getHostUserId());
            return false;
        }
        
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            logger.warn("대기 상태가 아닌 룸에서 게임 시작 시도: {} (상태: {})", roomId, room.getStatus());
            return false;
        }
        
        room.setStatus(Room.RoomStatus.ACTIVE);
        room.setCurrentQuestion(0);
        roomRepository.save(room); // DB에 상태 저장
        
        logger.info("게임 시작됨: 룸 {}", roomId);
        return true;
    }

    public String findRoomByInviteCode(String inviteCode) {
        Optional<Room> room = roomRepository.findByInviteCode(inviteCode);
        return room.map(Room::getId).orElse(null);
    }
} 