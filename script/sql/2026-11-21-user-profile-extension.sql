-- ============================================================
-- 增量迁移：sys_user 增加 DiceGo 个人资料字段
-- 日期：2026-11-21
-- 适用：已有 boardgame_online 库（ry_vue.sql 已把这两列并入 CREATE TABLE，
--       全新安装无需执行本文件）
-- 使用：mysql -u root -p boardgame_online < 2026-11-21-user-profile-extension.sql
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `sys_user`
    ADD COLUMN `age`            int(11)      DEFAULT NULL COMMENT '年龄（DiceGo 个人资料扩展）' AFTER `gender`,
    ADD COLUMN `fav_game_types` varchar(255) DEFAULT ''   COMMENT '偏好游戏类型分区 key，逗号分隔（DiceGo 个人资料扩展）' AFTER `age`;
