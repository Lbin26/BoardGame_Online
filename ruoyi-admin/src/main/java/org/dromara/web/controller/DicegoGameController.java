package org.dromara.web.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.web.domain.vo.GameDefinitionVO;
import org.dromara.web.mapper.DicegoGameDefinitionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏定义目录（只读、公开）：大厅游戏列表的唯一数据源，前端另按 create_time 打 NEW 标。
 */
@Slf4j
@RestController
@RequestMapping("/api/dicego/games")
@RequiredArgsConstructor
public class DicegoGameController {

    private final DicegoGameDefinitionMapper definitionMapper;

    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<GameDefinitionVO> games = definitionMapper.listDefinitions();
            result.put("success", true);
            result.put("games", games);
        } catch (Exception e) {
            log.warn("游戏定义查询失败: {}", e.getMessage());
            result.put("success", false);
            result.put("error", "游戏目录暂时不可用");
        }
        return result;
    }
}
