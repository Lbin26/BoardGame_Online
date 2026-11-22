package org.dromara.game.common.match.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.game.common.match.service.GameMatchHistoryService;
import org.dromara.game.common.match.vo.GameMatchRecentVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人战绩查询（全游戏共用）：当前登录用户的最近对局。
 *
 * <p>必须登录（不在白名单内），userId 只从 token 取，不接受前端传参，避免越权看他人战绩。
 */
@RestController
@RequestMapping("/api/game/matches")
@RequiredArgsConstructor
public class GameMatchHistoryController {

    private final GameMatchHistoryService historyService;

    @GetMapping("/recent")
    public Map<String, Object> recent(@RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> result = new HashMap<>();
        if (!LoginHelper.isLogin()) {
            result.put("success", false);
            result.put("error", "请先登录");
            return result;
        }
        List<GameMatchRecentVO> matches = historyService.recent(LoginHelper.getUserId(), limit);
        result.put("success", true);
        result.put("matches", matches);
        return result;
    }
}
