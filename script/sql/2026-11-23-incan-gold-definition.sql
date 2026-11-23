-- ============================================================
-- 增量迁移：登记印加宝藏（Incan Gold / Diamant）
-- 日期：2026-11-23
-- 适用：已有 boardgame_online 库（boardgame_online.sql 已把该行并入种子段，
--       全新安装无需执行本文件）
-- 说明：本游戏不建专属牌表（30 张探索牌 + 5 张神器是规则常量，由代码展开），
--       对局落库复用 game_match / game_match_player / game_action_log，
--       终局另写 game_player_stat 汇总战绩，供大厅「我的战绩」读取。
-- 使用：mysql -u root -p boardgame_online < 2026-11-23-incan-gold-definition.sql
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `game_definition`
    (`id`, `game_code`, `game_name`, `min_players`, `max_players`,
     `rule_version`, `icon`, `description`, `sort_no`, `status`, `create_time`)
VALUES (5, 'incan_gold', '印加宝藏', 3, 8, '1.0', '/posters/incanGold.webp',
        '见好就收的探险博弈：5 轮翻牌，宝石平分余数留牌，同种灾难再现即团灭，独走者可收走神器。',
        5, '0', NOW())
ON DUPLICATE KEY UPDATE
    `game_name`   = VALUES(`game_name`),
    `min_players` = VALUES(`min_players`),
    `max_players` = VALUES(`max_players`),
    `icon`        = VALUES(`icon`),
    `description` = VALUES(`description`),
    `sort_no`     = VALUES(`sort_no`);
