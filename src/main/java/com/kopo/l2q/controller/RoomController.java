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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
@Tag(name = "Room", description = "방 찾기 API")
public class RoomController {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);
    
    @Autowired
    private RoomService roomService;

    @GetMapping("/by-code")
    @Operation(summary = "초대 코드로 방 ID 조회", description = "초대 코드를 이용해 방의 고유 ID를 조회합니다.")
    public ResponseEntity<Map<String, String>> getRoomByCode(@RequestParam("code") String code) {
        logger.info("=== 초대코드로 룸 조회 API 호출 ===");
        logger.info("요청된 초대코드: {}", code);
        
        if (code == null || code.trim().isEmpty()) {
            logger.warn("초대코드가 비어있음");
            return ResponseEntity.badRequest().build();
        }
        
        // 대소문자 변환 확인
        String upperCode = code.toUpperCase();
        logger.info("대문자로 변환된 초대코드: {}", upperCode);
        
        Room room = roomService.getRoomByInviteCode(upperCode);
        if (room == null) {
            logger.warn("초대코드에 해당하는 룸을 찾을 수 없음: {}", upperCode);
            
            // 현재 캐시에 있는 모든 방의 초대코드를 출력 (디버깅용)
            logger.info("=== 현재 캐시에 있는 모든 방들 ===");
            // TODO: 이 부분은 roomService에 디버깅 메서드가 필요할 수 있음
            
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("roomId", room.getId());
        
        logger.info("룸 조회 성공: 초대코드 {} -> 룸 ID {}", upperCode, room.getId());
        logger.info("=== 초대코드로 룸 조회 API 응답 ===");
        logger.info("룸 ID: {}", response.get("roomId"));
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{inviteCode}")
    @Operation(summary = "초대 코드로 방 찾기", description = "초대 코드를 사용하여 해당 방의 ID를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "방 ID 조회 성공")
    @ApiResponse(responseCode = "404", description = "해당 초대 코드를 가진 방을 찾을 수 없음")
    public ResponseEntity<String> findRoomByInviteCode(
            @Parameter(description = "방 초대 코드", required = true, example = "AB12CD") @PathVariable String inviteCode) {
        String roomId = roomService.findRoomByInviteCode(inviteCode);
        if (roomId != null) {
            return ResponseEntity.ok(roomId);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
} 