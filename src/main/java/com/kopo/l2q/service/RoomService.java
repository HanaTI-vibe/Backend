package com.kopo.l2q.service;

import com.kopo.l2q.entity.Room;
import com.kopo.l2q.entity.Question;
import com.kopo.l2q.entity.Participant;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Participant>> participants = new ConcurrentHashMap<>();

    public String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }

    public Room createRoom(String roomId, String inviteCode, List<Question> questions, int timeLimit, boolean isMock) {
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
        return room;
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public Room getRoomByInviteCode(String inviteCode) {
        return rooms.values().stream()
                .filter(room -> room.getInviteCode().equals(inviteCode.toUpperCase()))
                .findFirst()
                .orElse(null);
    }

    public void addParticipant(String roomId, String userId, String userName, String socketId) {
        Map<String, Participant> roomParticipants = participants.get(roomId);
        if (roomParticipants != null) {
            Participant participant = new Participant();
            participant.setId(userId);
            participant.setName(userName);
            participant.setSocketId(socketId);
            participant.setScore(0);
            participant.setReady(false);
            roomParticipants.put(userId, participant);
        }
    }

    public void removeParticipant(String roomId, String userId) {
        Map<String, Participant> roomParticipants = participants.get(roomId);
        if (roomParticipants != null) {
            roomParticipants.remove(userId);
        }
    }

    public List<Participant> getRoomParticipants(String roomId) {
        Map<String, Participant> roomParticipants = participants.get(roomId);
        return roomParticipants != null ? new ArrayList<>(roomParticipants.values()) : new ArrayList<>();
    }

    public void updateParticipantScore(String roomId, String userId, int points) {
        Map<String, Participant> roomParticipants = participants.get(roomId);
        if (roomParticipants != null) {
            Participant participant = roomParticipants.get(userId);
            if (participant != null) {
                participant.setScore(participant.getScore() + points);
            }
        }
    }

    public void nextQuestion(String roomId) {
        Room room = rooms.get(roomId);
        if (room != null && room.getCurrentQuestion() < room.getQuestions().size() - 1) {
            room.setCurrentQuestion(room.getCurrentQuestion() + 1);
        } else if (room != null) {
            room.setStatus(Room.RoomStatus.FINISHED);
        }
    }
} 