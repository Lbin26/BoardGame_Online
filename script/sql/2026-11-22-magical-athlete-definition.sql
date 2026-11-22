-- ============================================================
-- 增量迁移：登记胡闹运动会（Magical Athlete）
-- 日期：2026-11-22
-- 适用：已有 boardgame_online 库（boardgame_online.sql 已把该行并入种子段，
--       全新安装无需执行本文件）
-- 说明：本游戏不建选手/能力表（D2：36 名是规则常量，代码展开；能力用事件注册表），
--       对局落库复用 game_match / game_match_player / game_action_log（D11）。
-- 使用：mysql -u root -p boardgame_online < 2026-11-22-magical-athlete-definition.sql
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `game_definition`
    (`id`, `game_code`, `game_name`, `min_players`, `max_players`,
     `rule_version`, `icon`, `description`, `sort_no`, `status`, `create_time`)
VALUES (4, 'magical_athlete', '胡闹运动会', 2, 6, '1.0', NULL,
        '轮抽+掷骰竞速：蛇形选4名选手，四场比赛各派1名，靠能力与骰点抢奖牌，总分最高胜。',
        4, '0', NOW())
ON DUPLICATE KEY UPDATE
    `game_name`   = VALUES(`game_name`),
    `min_players` = VALUES(`min_players`),
    `max_players` = VALUES(`max_players`),
    `description` = VALUES(`description`),
    `sort_no`     = VALUES(`sort_no`);
