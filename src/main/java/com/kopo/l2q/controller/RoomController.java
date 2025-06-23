package com.kopo.l2q.controller;

import com.kopo.l2q.entity.Room;
import com.kopo.l2q.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);
    
    @Autowired
    private RoomService roomService;

    @GetMapping("/by-code")
    public ResponseEntity<Map<String, String>> getRoomByCode(@RequestParam("code") String code) {
        logger.info("=== 초대코드로 룸 조회 API 호출 ===");
        logger.info("요청된 초대코드: {}", code);
        
        if (code == null || code.trim().isEmpty()) {
            logger.warn("초대코드가 비어있음");
            return ResponseEntity.badRequest().build();
        }
        
        Room room = roomService.getRoomByInviteCode(code);
        if (room == null) {
            logger.warn("초대코드에 해당하는 룸을 찾을 수 없음: {}", code);
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("roomId", room.getId());
        
        logger.info("룸 조회 성공: 초대코드 {} -> 룸 ID {}", code, room.getId());
        logger.info("=== 초대코드로 룸 조회 API 응답 ===");
        logger.info("룸 ID: {}", response.get("roomId"));
        
        return ResponseEntity.ok(response);
    }
} 