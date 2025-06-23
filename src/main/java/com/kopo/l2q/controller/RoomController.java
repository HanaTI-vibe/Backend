package com.kopo.l2q.controller;

import com.kopo.l2q.entity.Room;
import com.kopo.l2q.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {
    @Autowired
    private RoomService roomService;

    @GetMapping("/by-code")
    public ResponseEntity<Map<String, String>> getRoomByCode(@RequestParam("code") String code) {
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Room room = roomService.getRoomByInviteCode(code);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> response = new HashMap<>();
        response.put("roomId", room.getId());
        return ResponseEntity.ok(response);
    }
} 