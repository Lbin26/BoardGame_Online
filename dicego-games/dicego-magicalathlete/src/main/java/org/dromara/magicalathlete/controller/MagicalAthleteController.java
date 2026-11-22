package org.dromara.magicalathlete.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.game.common.domain.bo.CreateRoomBO;
import org.dromara.game.common.room.GameRoomManager;
import org.dromara.magicalathlete.domain.bo.MagicalAthleteSeatBO;
import org.dromara.magicalathlete.handler.MagicalAthleteWebSocketHandler;
import org.dromara.magicalathlete.manager.MagicalAthleteRoom;
import org.dromara.system.api.model.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 胡闹运动会房间生命周期 REST：建房（2-6）/入座/起身/查询。开局走 WS 的 START_GAME（房主）。
 */
@Slf4j
@RestController
@RequestMapping("/api/magical-athlete")
@RequiredArgsConstructor
public class MagicalAthleteController {

    private static final int DEFAULT_MAX_PLAYERS = 4;

    private final GameRoomManager<MagicalAthleteRoom> roomManager;
    private final MagicalAthleteWebSocketHandler webSocketHandler;

    @PostMapping("/rooms")
    public Map<String, Object> createRoom(@RequestBody CreateRoomBO bo, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (bo.getPlayerName() == null || bo.getPlayerName().isBlank()) {
            result.put("success", false);
            result.put("error", "玩家名不能为空");
            return result;
        }
        int maxPlayers = bo.getMaxPlayers() == null ? DEFAULT_MAX_PLAYERS : bo.getMaxPlayers();
        if (maxPlayers < MagicalAthleteRoom.MIN_PLAYERS || maxPlayers > MagicalAthleteRoom.MAX_PLAYERS) {
            result.put("success", false);
            result.put("error", "房间容量必须 2-6");
            return result;
        }
        MagicalAthleteRoom room = roomManager.createRoom(bo.getPlayerName(),
            id -> new MagicalAthleteRoom(id, bo.getPlayerName(), maxPlayers));
        room.bindUser(bo.getPlayerName(), resolveUserId(request));
        result.put("success", true);
        result.put("room", room.toVO(bo.getPlayerName()));
        return result;
    }

    @PostMapping("/rooms/{roomId}/seat")
    public Map<String, Object> takeSeat(@PathVariable String roomId, @RequestBody MagicalAthleteSeatBO bo,
                                        HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        MagicalAthleteRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        if (bo.getPlayerName() == null || bo.getPlayerName().isBlank()) {
            result.put("success", false);
            result.put("error", "玩家名不能为空");
            result.put("room", room.toVO(null));
            return result;
        }
        MagicalAthleteRoom.SeatResult seatResult = room.takeSeat(bo.getPlayerName(), bo.getSeatNo());
        switch (seatResult) {
            case SUCCESS -> {
                room.bindUser(bo.getPlayerName(), resolveUserId(request));
                result.put("success", true);
                webSocketHandler.broadcastSeatChange(roomId, bo.getPlayerName(), room.seatOf(bo.getPlayerName()));
            }
            case NOT_WAITING -> {
                result.put("success", false);
                result.put("error", "对局已开始，无法入座");
            }
            case SEAT_TAKEN -> {
                result.put("success", false);
                result.put("error", "该座位已被占用");
            }
            case ROOM_FULL -> {
                result.put("success", false);
                result.put("error", "房间已满");
            }
            case INVALID_SEAT -> {
                result.put("success", false);
                result.put("error", "座位号非法");
            }
        }
        result.put("room", room.toVO(bo.getPlayerName()));
        return result;
    }

    @PostMapping("/rooms/{roomId}/stand")
    public Map<String, Object> standUp(@PathVariable String roomId, @RequestBody MagicalAthleteSeatBO bo) {
        Map<String, Object> result = new HashMap<>();
        MagicalAthleteRoom room = roomManager.getRoom(roomId);
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
        result.put("room", room.toVO(bo.getPlayerName()));
        return result;
    }

    @GetMapping("/rooms/{roomId}")
    public Map<String, Object> getRoom(@PathVariable String roomId,
                                       @RequestParam(required = false) String playerName) {
        Map<String, Object> result = new HashMap<>();
        MagicalAthleteRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            result.put("success", false);
            result.put("error", "房间不存在");
            return result;
        }
        result.put("success", true);
        result.put("room", room.toVO(playerName));
        return result;
    }

    @GetMapping("/rooms")
    public Map<String, Object> listRooms() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (String roomId : roomManager.listRoomIds()) {
            MagicalAthleteRoom room = roomManager.getRoom(roomId);
            if (room == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("roomId", room.getRoomId());
            item.put("hostName", room.getHostName());
            item.put("maxPlayers", room.getMaxPlayers());
            item.put("seatedCount", room.seatedCount());
            item.put("status", room.getStatus());
            list.add(item);
        }
        result.put("rooms", list);
        result.put("waitingCount", roomManager.countWaiting());
        result.put("playingCount", roomManager.countPlaying());
        return result;
    }

    /** 白名单路由上的 token 还原：无 token / 过期 / 异常按游客（0）处理 */
    private Long resolveUserId(HttpServletRequest request) {
        if (request == null) return 0L;
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return 0L;
            LoginUser user = LoginHelper.getLoginUser(auth.substring(7).trim());
            return (user == null || user.getUserId() == null) ? 0L : user.getUserId();
        } catch (Exception e) {
            log.debug("token 还原用户失败（按游客处理）: {}", e.getMessage());
            return 0L;
        }
    }
}
