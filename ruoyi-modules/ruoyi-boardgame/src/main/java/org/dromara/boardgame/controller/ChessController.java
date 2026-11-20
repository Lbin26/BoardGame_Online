package org.dromara.boardgame.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.game.common.domain.bo.CreateRoomBO;
import org.dromara.game.common.domain.bo.SeatBO;
import org.dromara.boardgame.domain.vo.BoardSnapshotVO;
import org.dromara.game.common.domain.vo.RoomVO;
import org.dromara.game.common.enums.PlayerColor;
import org.dromara.boardgame.handler.ChessWebSocketHandler;
import org.dromara.boardgame.manager.ChessRoom;
import org.dromara.game.common.room.GameRoomManager;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chess")
@RequiredArgsConstructor
public class ChessController {

    private final GameRoomManager<ChessRoom> roomManager;
    private final ChessWebSocketHandler webSocketHandler;

    @PostMapping("/rooms")
    public Map<String, Object> createRoom(@RequestBody CreateRoomBO bo) {
        Map<String, Object> result = new HashMap<>();
        if (bo.getPlayerName() == null || bo.getPlayerName().isBlank()) {
            result.put("success", false);
            result.put("error", "玩家名不能为空");
            return result;
        }
        ChessRoom room = roomManager.createRoom(bo.getPlayerName());
        result.put("success", true);
        result.put("room", room.toVO(bo.getPlayerName()));
        result.put("board", room.getSnapshot());
        return result;
    }

    /** 手动选座：成功失败都返回当前 room+board，座位竞争败者据此立即重同步 */
    @PostMapping("/rooms/{roomId}/seat")
    public Map<String, Object> takeSeat(@PathVariable String roomId, @RequestBody SeatBO bo) {
        Map<String, Object> result = new HashMap<>();
        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        if (bo.getPlayerName() == null || bo.getPlayerName().isBlank()) {
            result.put("success", false);
            result.put("error", "玩家名不能为空");
            return result;
        }
        PlayerColor color;
        try {
            color = PlayerColor.valueOf(bo.getColor().toUpperCase());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "无效的颜色");
            fillRoom(result, room, bo.getPlayerName());
            return result;
        }
        ChessRoom.SeatResult seatResult = room.takeSeat(bo.getPlayerName(), color);
        switch (seatResult) {
            case SUCCESS -> {
                result.put("success", true);
                webSocketHandler.broadcastSeatChange(roomId, bo.getPlayerName(), color);
            }
            case NOT_WAITING -> {
                result.put("success", false);
                result.put("error", "对局已开始，无法选座");
            }
            case SEAT_TAKEN -> {
                result.put("success", false);
                result.put("error", "该座位已被占用");
            }
        }
        fillRoom(result, room, bo.getPlayerName());
        return result;
    }

    /** 起身离座（仅等待中）：同样返回当前 room+board */
    @PostMapping("/rooms/{roomId}/stand")
    public Map<String, Object> standUp(@PathVariable String roomId, @RequestBody SeatBO bo) {
        Map<String, Object> result = new HashMap<>();
        ChessRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        if (bo.getPlayerName() == null || bo.getPlayerName().isBlank()) {
            result.put("success", false);
            result.put("error", "玩家名不能为空");
            return result;
        }
        if (room.standUp(bo.getPlayerName())) {
            result.put("success", true);
            webSocketHandler.broadcastSeatChange(roomId, bo.getPlayerName(), null);
        } else {
            result.put("success", false);
            result.put("error", "当前无法起身");
        }
        fillRoom(result, room, bo.getPlayerName());
        return result;
    }

    private void fillRoom(Map<String, Object> result, ChessRoom room, String viewerName) {
        result.put("room", room.toVO(viewerName));
        result.put("board", room.getSnapshot());
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
