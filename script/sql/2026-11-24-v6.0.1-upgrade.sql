-- ============================================================
-- DiceGo v6.0.1 服务器升级脚本（一次性、可重复执行）
-- 日期：2026-11-24
-- 适用库：boardgame_online（已有库升级；全新安装直接用 boardgame_online.sql）
-- 用法：mysql -u root -p boardgame_online < 2026-11-24-v6.0.1-upgrade.sql
--
-- 覆盖本次 v6.0.1 发布所需的全部数据变更：
--   1. sys_user 个人资料扩展列（原 2026-11-21-user-profile-extension.sql）
--   2. game_match / game_match_player / game_action_log 逻辑删除列与索引
--      （原 2026-11-24-match-logical-delete.sql）
--   3. 角色口径统一：仅「欧洲小跪族」为 superadmin，其余在用账号为普通用户
--      （原 2026-11-24-role-update.sql）
--   4. game_definition 扩列 + 收录全部 10 款游戏，作为大厅游戏列表数据源
--      （原 2026-11-24-game-definition-catalog.sql，已含前三款游戏的种子数据，
--        无需再单独执行 11-21/11-22/11-23 的三份 definition 脚本）
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，统一用 information_schema + PREPARE 做幂等。
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1. sys_user 个人资料扩展列 ----------
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `age` int DEFAULT NULL COMMENT ''年龄（DiceGo 个人资料扩展）'' AFTER `gender`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'age');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `fav_game_types` varchar(255) DEFAULT '''' COMMENT ''偏好游戏类型分区 key，逗号分隔（DiceGo 个人资料扩展）'' AFTER `age`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'fav_game_types');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 对局三表逻辑删除 ----------
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_match` ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT ''0'' COMMENT ''删除标志（0存在 1删除）'' AFTER `create_time`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_match' AND COLUMN_NAME = 'del_flag');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_match` ADD KEY `idx_match_del` (`del_flag`)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_match' AND INDEX_NAME = 'idx_match_del');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_match_player` ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT ''0'' COMMENT ''删除标志（0存在 1删除）'' AFTER `create_time`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_match_player' AND COLUMN_NAME = 'del_flag');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_match_player` ADD KEY `idx_mplayer_del` (`del_flag`)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_match_player' AND INDEX_NAME = 'idx_mplayer_del');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_action_log` ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT ''0'' COMMENT ''删除标志（0存在 1删除）'' AFTER `execute_time`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_action_log' AND COLUMN_NAME = 'del_flag');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_action_log` ADD KEY `idx_action_del` (`del_flag`)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_action_log' AND INDEX_NAME = 'idx_action_del');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 角色口径统一 ----------
SET @common_role_id = (SELECT role_id FROM sys_role WHERE role_key = 'common' AND del_flag = '0' LIMIT 1);
INSERT INTO sys_role
    (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
     dept_check_strictly, status, del_flag, create_dept, create_by, create_time,
     update_by, update_time, remark)
SELECT 1761300000000000002, '普通用户', 'common', 2, '5', 1, 1, '0', '0',
       1761000000000000103, 1761100000000000001, NOW(), NULL, NULL, 'DiceGo 平台普通用户'
WHERE @common_role_id IS NULL;
SET @common_role_id = (SELECT role_id FROM sys_role WHERE role_key = 'common' AND del_flag = '0' LIMIT 1);
SET @super_role_id = (SELECT role_id FROM sys_role WHERE role_key = 'superadmin' AND del_flag = '0' LIMIT 1);

UPDATE sys_role SET del_flag = '1', update_time = NOW()
WHERE role_key IN ('test1', 'test2') AND del_flag = '0';

DELETE FROM sys_user_role
WHERE user_id IN (SELECT user_id FROM sys_user WHERE del_flag = '0');

INSERT INTO sys_user_role (user_id, role_id)
SELECT user_id, @common_role_id
FROM sys_user
WHERE del_flag = '0' AND user_name <> '欧洲小跪族' AND nick_name <> '欧洲小跪族';

INSERT INTO sys_user_role (user_id, role_id)
SELECT user_id, @super_role_id
FROM sys_user
WHERE del_flag = '0' AND (user_name = '欧洲小跪族' OR nick_name = '欧洲小跪族');

-- ---------- 4. game_definition 成为游戏列表数据源 ----------
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_definition` ADD COLUMN `zone_code` varchar(32) DEFAULT NULL COMMENT ''分区编码'' AFTER `game_name`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_definition' AND COLUMN_NAME = 'zone_code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_definition` ADD COLUMN `playable` char(1) NOT NULL DEFAULT ''0'' COMMENT ''是否可玩（0否 1是）'' AFTER `zone_code`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_definition' AND COLUMN_NAME = 'playable');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_definition` ADD COLUMN `mechanisms` varchar(255) DEFAULT NULL COMMENT ''机制标签，逗号分隔'' AFTER `description`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_definition' AND COLUMN_NAME = 'mechanisms');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `game_definition` ADD COLUMN `story` varchar(1000) DEFAULT NULL COMMENT ''房间层背景故事'' AFTER `mechanisms`',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_definition' AND COLUMN_NAME = 'story');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `game_definition`
    (`id`, `game_code`, `game_name`, `min_players`, `max_players`, `rule_version`,
     `icon`, `description`, `zone_code`, `playable`, `mechanisms`, `story`,
     `sort_no`, `status`, `del_flag`, `create_time`)
VALUES
    (1, 'splendor', '璀璨宝石 Splendor', 2, 4, '1.0',
     '/posters/splendor.webp', '引擎构建 · 成套收集 · 2-4 人', 'card', '1',
     '引擎构建,成套收集',
     '文艺复兴的城邦里，宝石商人争相囤积矿脉、延请工匠、结交贵族。谁先把有限的宝石变成源源不断产出声望的商业帝国，谁就赢得这座城市。',
     1, '0', '0', '2026-11-01 00:00:00'),
    (2, 'xiangqi', '象棋', 2, 2, '1.0',
     NULL, '楚河汉界，运筹帷幄', 'abstract', '1', NULL,
     '楚河汉界之间，车马炮列阵以待。千年攻守浓缩在方寸棋盘上，读透对手的意图，比对手多算一步，就是胜利。',
     2, '0', '0', '2026-11-01 00:00:00'),
    (3, 'halli_galli', '德国心脏病 Halli Galli', 2, 6, '1.0',
     '/posters/halliGalli.webp', '反应 · 同时行动 · 2-6 人', 'party', '1',
     '反应,同时行动',
     '果香四溢的牌堆一张接一张翻开，五枚相同水果一出现，铃声就是号令。比眼睛更快、比手更稳的人，才能把整桌牌收进怀里。',
     3, '0', '0', '2026-11-01 00:00:00'),
    (4, 'magical_athlete', '胡闹运动会 Magical Athlete', 2, 6, '1.0',
     '/posters/magicalAthlete.webp', '轮抽 · 掷骰竞速 · 角色能力 · 2-6 人', 'party', '1',
     '轮抽,骰子,角色能力',
     '一群性格古怪的选手踏上三十格赛道：香蕉绊人、飞艇传送、倒霉蛋垫底也要加分。轮抽出场阵容，在混乱与笑料里抢下金牌。',
     4, '0', '0', '2026-11-01 00:00:00'),
    (5, 'incan_gold', '印加宝藏 Diamant', 3, 8, '1.0',
     '/posters/incanGold.webp', '推运气 · 探险收集 · 3-8 人', 'party', '1',
     '推运气,探险收集',
     '手电照亮神庙深处，宝石在火光里闪烁，蛇、蜘蛛与毒气却潜伏在下一张牌后。是见好就收带着宝石撤退，还是再赌一把深入宝藏？贪婪与恐惧只在一步之间。',
     5, '0', '0', '2026-11-01 00:00:00'),
    (6, 'go', '围棋', 2, 2, '1.0',
     NULL, '敬请期待', 'abstract', '0', NULL,
     '黑白子落下，格局随之生长。围地与侵消之间，最简单的规则孕育出最深的算度。',
     6, '0', '0', '2026-11-01 00:00:00'),
    (7, 'gomoku', '五子棋', 2, 2, '1.0',
     NULL, '敬请期待', 'abstract', '0', NULL,
     '五子连珠，攻守瞬息易位。看似简单的横线，考验的是藏在日常里的先见之明。',
     7, '0', '0', '2026-11-01 00:00:00'),
    (8, 'dice_throne', '王权骰铸 Dice Throne', 2, 4, '1.0',
     '/posters/diceThrone.webp', '敬请期待', 'narrative', '0',
     '骰子驱动,角色能力（不对称）,卡牌辅助',
     '英雄各持一枚命运之骰，组合出专属战技。掷出的点数既是武器，也是需要管理人品与风险的赌注。',
     8, '0', '0', '2026-11-01 00:00:00'),
    (9, 'avalon', '阿瓦隆 Avalon', 5, 10, '1.0',
     '/posters/avalon.webp', '敬请期待', 'narrative', '0',
     '隐藏身份,推理/演绎,投票',
     '圆桌之上，忠诚与叛逆同席而坐。任务成败取决于你信谁、不信谁——而最危险的那个人，往往就坐在你身边。',
     9, '0', '0', '2026-11-01 00:00:00'),
    (10, 'brass', '伯明翰 Brass: Birmingham', 2, 4, '1.0',
     '/posters/brass.webp', '敬请期待', 'strategy', '0',
     '网络构建,经济/资源管理,手牌管理（轻卡驱）',
     '蒸汽与焦炭推动伯明翰的运河和铁路。建厂、铺轨、运煤、卖酒，在两个时代的产业浪潮里把小作坊经营成城市命脉。',
     10, '0', '0', '2026-11-01 00:00:00')
ON DUPLICATE KEY UPDATE
    `game_name`   = VALUES(`game_name`),
    `min_players` = VALUES(`min_players`),
    `max_players` = VALUES(`max_players`),
    `icon`        = VALUES(`icon`),
    `description` = VALUES(`description`),
    `zone_code`   = VALUES(`zone_code`),
    `playable`    = VALUES(`playable`),
    `mechanisms`  = VALUES(`mechanisms`),
    `story`       = VALUES(`story`),
    `sort_no`     = VALUES(`sort_no`),
    `status`      = '0',
    `del_flag`    = '0';

-- ---------- 升级后自检 ----------
-- SELECT COUNT(*) AS games FROM game_definition WHERE del_flag = '0';
-- SELECT u.user_name, r.role_key FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.user_id
--   JOIN sys_role r ON r.role_id = ur.role_id WHERE u.del_flag = '0';
