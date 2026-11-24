-- ============================================================
-- 对局三表改为逻辑删除（全游戏共用，仅执行一次）
--
-- 背景：此前 GameMatchCleanupTask 对 game_match / game_match_player /
-- game_action_log 做物理删除，导致战绩查询无法区分"已清理"和"从未存在"，
-- 且历史数据不可追溯。本脚本给三表补 del_flag，清理任务改为标记删除，
-- 战绩查询（GameMatchHistoryService）只查 del_flag='0' 的活跃记录。
--
-- 适用库：boardgame_online；执行后无需重启，Java 侧改动随应用发布生效。
-- ============================================================

ALTER TABLE `game_match`
    ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）' AFTER `create_time`,
    ADD KEY `idx_match_del` (`del_flag`);

ALTER TABLE `game_match_player`
    ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）' AFTER `create_time`,
    ADD KEY `idx_mplayer_del` (`del_flag`);

ALTER TABLE `game_action_log`
    ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 1删除）' AFTER `execute_time`,
    ADD KEY `idx_action_del` (`del_flag`);
