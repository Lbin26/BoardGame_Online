-- ============================================================
-- 增量迁移：登记德国心脏病（Halli Galli）
-- 日期：2026-11-21
-- 适用：已有 boardgame_online 库（boardgame_online.sql 已把该行并入种子段，
--       全新安装无需执行本文件）
-- 说明：本游戏不建专属表（D3：20 种牌型是规则常量，由代码展开为 56 张），
--       对局落库复用 game_match / game_match_player / game_action_log。
-- 使用：mysql -u root -p boardgame_online < 2026-11-21-halli-galli-definition.sql
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `game_definition`
    (`id`, `game_code`, `game_name`, `min_players`, `max_players`,
     `rule_version`, `icon`, `description`, `sort_no`, `status`, `create_time`)
VALUES (3, 'halli_galli', '德国心脏病', 2, 6, '1.0', NULL,
        '反应类实时对战：同种水果恰好5个时抢铃，抢错赔牌，牌多者胜。',
        3, '0', NOW())
ON DUPLICATE KEY UPDATE
    `game_name`   = VALUES(`game_name`),
    `min_players` = VALUES(`min_players`),
    `max_players` = VALUES(`max_players`),
    `description` = VALUES(`description`),
    `sort_no`     = VALUES(`sort_no`);
