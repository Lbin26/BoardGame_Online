package org.dromara.boardgame.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.boardgame.domain.bo.CreateRoomBO;
import org.dromara.boardgame.domain.bo.JoinRoomBO;
import org.dromara.boardgame.domain.vo.BoardSnapshotVO;
import org.dromara.boardgame.domain.vo.RoomVO;
import org.dromara.boardgame.engine.ChessBoard;
import org.dromara.boardgame.engine.ChessPiece;
import org.dromara.boardgame.enums.PlayerColor;
import org.dromara.boardgame.manager.ChessRoom;
import org.dromara.boardgame.manager.RoomManager;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chess")
@RequiredArgsConstructor
public class ChessController {

    private final RoomManager roomManager;

    @PostMapping("/rooms")
    public Map<String, Object> createRoom(@RequestBody CreateRoomBO bo) {
        Map<String, Object> result = new HashMap<>();
        String name = bo.getPlayerName() == null || bo.getPlayerName().isBlank()
            ? "玩家" + (int)(Math.random() * 1000) : bo.getPlayerName();
        ChessRoom room = roomManager.createRoom(name);
        result.put("success", true);
        result.put("room", room.toVO(name));
        result.put("board", room.getSnapshot());
        return result;
    }

    @PostMapping("/rooms/join")
    public Map<String, Object> joinRoom(@RequestBody JoinRoomBO bo) {
        Map<String, Object> result = new HashMap<>();
        ChessRoom room = roomManager.getRoom(bo.getRoomId());
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        String name = bo.getPlayerName() == null || bo.getPlayerName().isBlank()
            ? "玩家" + (int)(Math.random() * 1000) : bo.getPlayerName();

        PlayerColor existing = room.getPlayerColor(name);
        if (existing != null) {
            result.put("success", true);
            result.put("room", room.toVO(name));
            result.put("board", room.getSnapshot());
            return result;
        }

        if (room.getBlackPlayerName() == null && room.getStatus() == org.dromara.boardgame.enums.RoomStatus.WAITING) {
            if (room.joinAsBlack(name)) {
                result.put("success", true);
                result.put("room", room.toVO(name));
                result.put("board", room.getSnapshot());
                return result;
            }
        }

        result.put("success", false);
        result.put("error", "房间已满或已开始");
        return result;
    }

    @GetMapping("/rooms/{roomId}")
    public Map<String, Object> getRoom(@PathVariable String roomId,
                                       @RequestParam(required = false) String playerName) {
        Map<String, Object> result = new HashMap<>();
        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        result.put("success", true);
        result.put("room", room.toVO(playerName));
        result.put("board", room.getSnapshot());
        return result;
    }

    @GetMapping("/rooms")
    public Map<String, Object> listRooms() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChessRoom room : roomManager.listRoomIds().stream().map(roomManager::getRoom).toList()) {
            if (room != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("roomId", room.getRoomId());
                item.put("redPlayerName", room.getRedPlayerName());
                item.put("blackPlayerName", room.getBlackPlayerName());
                item.put("status", room.getStatus());
                list.add(item);
            }
        }
        result.put("rooms", list);
        result.put("waitingCount", roomManager.countWaiting());
        result.put("playingCount", roomManager.countPlaying());
        return result;
    }

    @GetMapping("/rooms/{roomId}/valid-moves")
    public Map<String, Object> getValidMoves(@PathVariable String roomId,
                                              @RequestParam int row,
                                              @RequestParam int col) {
        Map<String, Object> result = new HashMap<>();
        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        List<int[]> moves = room.getValidMoves(row, col);
        List<Map<String, Integer>> list = new ArrayList<>();
        for (int[] m : moves) {
            Map<String, Integer> p = new HashMap<>();
            p.put("row", m[0]);
            p.put("col", m[1]);
            list.add(p);
        }
        result.put("success", true);
        result.put("validMoves", list);
        return result;
    }

    @PostMapping("/rooms/{roomId}/reset")
    public Map<String, Object> resetRoom(@PathVariable String roomId) {
        Map<String, Object> result = new HashMap<>();
        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        room.reset();
        result.put("success", true);
        result.put("room", room.toVO(null));
        return result;
    }
}
