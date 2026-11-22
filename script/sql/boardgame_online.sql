-- ============================================================
-- BoardGame_Online 桌游平台业务表全量建表脚本（仅 CREATE，无 ALTER）
-- 使用：mysql -u user -p boardgame_online < boardgame_online.sql
--
-- 表结构分两层：
--   1. 平台公共层（游戏无关）：game_definition / game_room / game_room_seat /
--      game_match / game_match_player / game_player_stat / game_action_log
--   2. 游戏专属层（单游戏自己的关联表，以 <game>_ 前缀命名）：
--      splendor_card / splendor_noble
--      新增桌游时：总表登记一行 game_definition，专属表照此模式另建。
--
-- 种子数据来源：design/games/splendor/game.yaml（90 发展卡 + 10 贵族，
-- 官方卡表四源交叉比对定稿），card_code/noble_code 与 game.yaml 的 key 一致，
-- 修改卡表数值须先改 game.yaml 再重新生成，勿直接手改本文件数值。
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、平台公共层
-- ============================================================

-- 1.1 桌游总表
CREATE TABLE `game_definition`
(
    `id`           bigint       NOT NULL COMMENT '主键',
    `game_code`    varchar(32)  NOT NULL COMMENT '游戏编码，如 splendor',
    `game_name`    varchar(64)  NOT NULL COMMENT '游戏名称，如 宝石商人',
    `min_players`  tinyint      NOT NULL DEFAULT '2' COMMENT '最少开局人数',
    `max_players`  tinyint      NOT NULL DEFAULT '4' COMMENT '最多开局人数',
    `rule_version` varchar(16)  NOT NULL DEFAULT '1.0' COMMENT '规则版本号',
    `icon`         varchar(255)         DEFAULT NULL COMMENT '游戏图标路径（大厅展示）',
    `description`  varchar(500)         DEFAULT NULL COMMENT '游戏简介（大厅展示）',
    `sort_no`      int          NOT NULL DEFAULT '0' COMMENT '排序号（越小越靠前）',
    `status`       char(1)               DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `del_flag`     char(1)               DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `create_dept`  bigint               DEFAULT NULL COMMENT '创建部门',
    `create_by`    bigint               DEFAULT NULL COMMENT '创建者',
    `create_time`  datetime             DEFAULT NULL COMMENT '创建时间',
    `update_by`    bigint               DEFAULT NULL COMMENT '更新者',
    `update_time`  datetime             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_game_code` (`game_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '游戏定义表';

-- 1.2 游戏房间表
CREATE TABLE `game_room`
(
    `id`               bigint      NOT NULL COMMENT '主键',
    `room_no`          varchar(8)  NOT NULL COMMENT '房间号（6位数字，供玩家口头分享）',
    `game_code`        varchar(32) NOT NULL DEFAULT 'splendor' COMMENT '游戏编码',
    `host_user_id`     bigint      NOT NULL COMMENT '房主用户ID',
    `max_players`      tinyint     NOT NULL DEFAULT '4' COMMENT '最大人数（按创建时选择锁定）',
    `seat_count`       tinyint     NOT NULL DEFAULT '0' COMMENT '当前已入座人数',
    `room_status`      char(1)     NOT NULL DEFAULT '0' COMMENT '房间状态（0等待中 1对局中 2已结束）',
    `private_flag`      char(1)     NOT NULL DEFAULT '0' COMMENT '是否私密房（0公开 1私密）',
    `room_password`    varchar(64)          DEFAULT NULL COMMENT '房间密码（加盐哈希，私密房才填）',
    `current_match_id` bigint               DEFAULT NULL COMMENT '当前进行中的对局ID',
    `create_dept`      bigint               DEFAULT NULL COMMENT '创建部门',
    `create_by`        bigint               DEFAULT NULL COMMENT '创建者',
    `create_time`      datetime             DEFAULT NULL COMMENT '创建时间',
    `update_by`        bigint               DEFAULT NULL COMMENT '更新者',
    `update_time`      datetime             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_no` (`room_no`),
    KEY `idx_room_status` (`room_status`),
    KEY `idx_host` (`host_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '游戏房间表';

-- 1.3 房间座位表
CREATE TABLE `game_room_seat`
(
    `id`             bigint      NOT NULL COMMENT '主键',
    `room_id`        bigint      NOT NULL COMMENT '房间ID',
    `seat_no`        tinyint     NOT NULL COMMENT '座位号（0-3）',
    `user_id`        bigint      NOT NULL COMMENT '玩家用户ID（外挂 sys_user）',
    `nick_name`      varchar(64)          DEFAULT NULL COMMENT '玩家昵称快照（免联表展示）',
    `ready_flag`     char(1)     NOT NULL DEFAULT '0' COMMENT '准备状态（0未准备 1已准备）',
    `online_flag`    char(1)     NOT NULL DEFAULT '1' COMMENT '在线状态（0离线 1在线）',
    `last_heartbeat` datetime             DEFAULT NULL COMMENT '最后心跳时间',
    `enter_time`     datetime             DEFAULT NULL COMMENT '进入房间时间',
    `create_dept`    bigint               DEFAULT NULL COMMENT '创建部门',
    `create_by`      bigint               DEFAULT NULL COMMENT '创建者',
    `create_time`    datetime             DEFAULT NULL COMMENT '创建时间',
    `update_by`      bigint               DEFAULT NULL COMMENT '更新者',
    `update_time`    datetime             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_seat` (`room_id`, `seat_no`),
    UNIQUE KEY `uk_room_user` (`room_id`, `user_id`),
    KEY `idx_seat_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '房间座位表';

-- 1.4 对局表
CREATE TABLE `game_match`
(
    `id`            bigint       NOT NULL COMMENT '主键',
    `match_no`      varchar(32)  NOT NULL COMMENT '对局编号（对外展示，如 M20261005xxxx）',
    `room_no`       varchar(8)   NOT NULL COMMENT '房间号快照（房间会被删除，故冗余保存）',
    `game_code`     varchar(32)  NOT NULL DEFAULT 'splendor' COMMENT '游戏编码',
    `player_count`  tinyint      NOT NULL COMMENT '本局玩家数 2-4',
    `random_seed`   bigint       NOT NULL COMMENT '随机种子（复盘/测试的核心）',
    `seat_order`    varchar(64)  NOT NULL COMMENT '座位顺序，按座位号升序的用户ID逗号串',
    `match_status`  char(1)      NOT NULL DEFAULT '1' COMMENT '对局状态（0初始化 1进行中 2已结束 3异常中止）',
    `winner_user_id` bigint               DEFAULT NULL COMMENT '获胜用户ID',
    `total_rounds`  int                   DEFAULT NULL COMMENT '总回合数',
    `start_time`    datetime              DEFAULT NULL COMMENT '开局时间',
    `end_time`      datetime              DEFAULT NULL COMMENT '结束时间',
    `create_dept`   bigint                DEFAULT NULL COMMENT '创建部门',
    `create_by`     bigint                DEFAULT NULL COMMENT '创建者',
    `create_time`   datetime              DEFAULT NULL COMMENT '创建时间',
    `update_by`     bigint                DEFAULT NULL COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_match_no` (`match_no`),
    KEY `idx_match_room` (`room_no`),
    KEY `idx_match_status` (`match_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '对局表';

-- 1.5 对局玩家战绩表
CREATE TABLE `game_match_player`
(
    `id`          bigint  NOT NULL COMMENT '主键',
    `match_id`    bigint  NOT NULL COMMENT '对局ID',
    `user_id`     bigint  NOT NULL COMMENT '玩家用户ID',
    `seat_no`     tinyint NOT NULL COMMENT '本局座位号',
    `final_score` int     NOT NULL DEFAULT '0' COMMENT '最终声望分',
    `card_count`  int     NOT NULL DEFAULT '0' COMMENT '购买卡牌数（同分判定依据）',
    `noble_count` int     NOT NULL DEFAULT '0' COMMENT '获得贵族数',
    `rank_no`     tinyint NOT NULL DEFAULT '0' COMMENT '名次（1=冠军）',
    `win_flag`    char(1) NOT NULL DEFAULT '0' COMMENT '是否胜者（0否 1是）',
    `quit_flag`   char(1) NOT NULL DEFAULT '0' COMMENT '是否中途退出/掉线判负（0否 1是）',
    `create_dept` bigint           DEFAULT NULL COMMENT '创建部门',
    `create_by`   bigint           DEFAULT NULL COMMENT '创建者',
    `create_time` datetime         DEFAULT NULL COMMENT '创建时间',
    `update_by`   bigint           DEFAULT NULL COMMENT '更新者',
    `update_time` datetime         DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_match_user` (`match_id`, `user_id`),
    KEY `idx_mplayer_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '对局玩家战绩表';

-- 1.6 玩家战绩统计表
CREATE TABLE `game_player_stat`
(
    `id`             bigint   NOT NULL COMMENT '主键',
    `user_id`        bigint   NOT NULL COMMENT '玩家用户ID',
    `total_matches`  int      NOT NULL DEFAULT '0' COMMENT '总场次',
    `win_matches`    int      NOT NULL DEFAULT '0' COMMENT '胜场',
    `lose_matches`   int      NOT NULL DEFAULT '0' COMMENT '负场',
    `highest_score`  int      NOT NULL DEFAULT '0' COMMENT '单局最高分',
    `total_score`    bigint   NOT NULL DEFAULT '0' COMMENT '累计得分（用于算场均）',
    `last_play_time` datetime          DEFAULT NULL COMMENT '最近一次对局时间',
    `del_flag`       char(1)           DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `create_dept`    bigint            DEFAULT NULL COMMENT '创建部门',
    `create_by`      bigint            DEFAULT NULL COMMENT '创建者',
    `create_time`    datetime          DEFAULT NULL COMMENT '创建时间',
    `update_by`      bigint            DEFAULT NULL COMMENT '更新者',
    `update_time`    datetime          DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '玩家战绩统计表';

-- 1.7 对局行动日志表（只追加）
CREATE TABLE `game_action_log`
(
    `id`            bigint        NOT NULL COMMENT '主键',
    `match_id`      bigint        NOT NULL COMMENT '对局ID',
    `round_no`      int           NOT NULL DEFAULT '1' COMMENT '回合号',
    `seat_no`       tinyint       NOT NULL COMMENT '行动者座位号',
    `user_id`       bigint        NOT NULL COMMENT '行动者用户ID',
    `action_id`     varchar(64)   NOT NULL COMMENT '客户端生成的动作唯一ID（幂等去重键）',
    `action_type`   varchar(32)   NOT NULL COMMENT '动作类型（TAKE_THREE/TAKE_TWO/BUY_CARD/RESERVE_CARD/DISCARD）',
    `action_params` varchar(1000)          DEFAULT NULL COMMENT '动作参数 JSON',
    `state_version` int           NOT NULL DEFAULT '0' COMMENT '动作执行后的状态版本号',
    `execute_time`  datetime      NOT NULL COMMENT '服务端执行时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_match_action` (`match_id`, `action_id`),
    KEY `idx_match_round` (`match_id`, `round_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '对局行动日志表（只追加）';

-- ============================================================
-- 二、Splendor（璀璨宝石）专属表
-- 加成/需求五色编码：0白 1蓝 2绿 3红 4黑（黄金不参与编码）
-- ============================================================

-- 2.1 发展卡（40/30/20）
CREATE TABLE `splendor_card`
(
    `id`          bigint      NOT NULL COMMENT '主键',
    `card_code`   varchar(16) NOT NULL COMMENT '卡牌业务编码，与 game.yaml key 一致（如 l1-001），稳定不随主键变化',
    `card_level`  tinyint     NOT NULL COMMENT '卡牌等级 1/2/3',
    `bonus`       tinyint     NOT NULL COMMENT '产出宝石色 0白 1蓝 2绿 3红 4黑',
    `prestige`    tinyint     NOT NULL DEFAULT '0' COMMENT '声望分（0-5）',
    `cost_white`  tinyint     NOT NULL DEFAULT '0' COMMENT '购买成本-白',
    `cost_blue`   tinyint     NOT NULL DEFAULT '0' COMMENT '购买成本-蓝',
    `cost_green`  tinyint     NOT NULL DEFAULT '0' COMMENT '购买成本-绿',
    `cost_red`    tinyint     NOT NULL DEFAULT '0' COMMENT '购买成本-红',
    `cost_black`  tinyint     NOT NULL DEFAULT '0' COMMENT '购买成本-黑',
    `del_flag`    char(1)              DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `create_dept` bigint               DEFAULT NULL COMMENT '创建部门',
    `create_by`   bigint               DEFAULT NULL COMMENT '创建者',
    `create_time` datetime             DEFAULT NULL COMMENT '创建时间',
    `update_by`   bigint               DEFAULT NULL COMMENT '更新者',
    `update_time` datetime             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_card_code` (`card_code`),
    KEY `idx_level` (`card_level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '宝石商人-发展卡';

-- 2.2 贵族（10 张，固定 3VP）
CREATE TABLE `splendor_noble`
(
    `id`          bigint      NOT NULL COMMENT '主键',
    `noble_code`  varchar(16) NOT NULL COMMENT '贵族业务编码，与 game.yaml key 一致（如 noble-01）',
    `noble_name`  varchar(32)          DEFAULT NULL COMMENT '贵族名称（展示用，可空）',
    `prestige`    tinyint     NOT NULL DEFAULT '3' COMMENT '声望分（固定 3）',
    `need_white`  tinyint     NOT NULL DEFAULT '0' COMMENT '要求白卡数量',
    `need_blue`   tinyint     NOT NULL DEFAULT '0' COMMENT '要求蓝卡数量',
    `need_green`  tinyint     NOT NULL DEFAULT '0' COMMENT '要求绿卡数量',
    `need_red`    tinyint     NOT NULL DEFAULT '0' COMMENT '要求红卡数量',
    `need_black`  tinyint     NOT NULL DEFAULT '0' COMMENT '要求黑卡数量',
    `del_flag`    char(1)              DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `create_dept` bigint               DEFAULT NULL COMMENT '创建部门',
    `create_by`   bigint               DEFAULT NULL COMMENT '创建者',
    `create_time` datetime             DEFAULT NULL COMMENT '创建时间',
    `update_by`   bigint               DEFAULT NULL COMMENT '更新者',
    `update_time` datetime             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noble_code` (`noble_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '宝石商人-贵族';

-- ============================================================
-- 三、种子数据
-- ============================================================

-- 3.1 桌游总表（id 固定，业务代码/房间表以 game_code 关联而非 id）
INSERT INTO `game_definition` (`id`, `game_code`, `game_name`, `min_players`, `max_players`, `rule_version`, `icon`, `description`, `sort_no`, `status`, `create_time`)
VALUES (1, 'splendor', '宝石商人', 2, 4, '1.0', NULL, '引擎构建+成套收集：拿宝石、买发展卡、赢贵族拜访，率先达到15声望。', 1, '0', NOW()),
       (2, 'xiangqi', '中国象棋', 2, 2, '1.0', NULL, '双人传统棋类对弈，支持悔棋、认输、重开。', 2, '0', NOW()),
       (3, 'halli_galli', '德国心脏病', 2, 6, '1.0', NULL, '反应类实时对战：同种水果恰好5个时抢铃，抢错赔牌，牌多者胜。', 3, '0', NOW()),
       (4, 'magical_athlete', '胡闹运动会', 2, 6, '1.0', NULL, '轮抽+掷骰竞速：蛇形选4名选手，四场比赛各派1名，靠能力与骰点抢奖牌，总分最高胜。', 4, '0', NOW());

-- 3.2/3.3 Splendor 卡牌与贵族种子由 design/games/splendor/game.yaml 生成（见文件头说明）
-- 3.2 宝石商人-发展卡种子（90 张，由 game.yaml 生成，勿手改）
INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (1, 'l1-001', 1, 0, 0, 0, 3, 0, 0, 0, NOW()),
  (2, 'l1-002', 1, 0, 0, 0, 0, 0, 2, 1, NOW()),
  (3, 'l1-003', 1, 0, 0, 0, 1, 1, 1, 1, NOW()),
  (4, 'l1-004', 1, 0, 0, 0, 2, 0, 0, 2, NOW()),
  (5, 'l1-005', 1, 0, 1, 0, 0, 4, 0, 0, NOW()),
  (6, 'l1-006', 1, 0, 0, 0, 1, 2, 1, 1, NOW()),
  (7, 'l1-007', 1, 0, 0, 0, 2, 2, 0, 1, NOW()),
  (8, 'l1-008', 1, 0, 0, 3, 1, 0, 0, 1, NOW()),
  (9, 'l1-009', 1, 1, 0, 1, 0, 0, 0, 2, NOW()),
  (10, 'l1-010', 1, 1, 0, 0, 0, 0, 0, 3, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (11, 'l1-011', 1, 1, 0, 1, 0, 1, 1, 1, NOW()),
  (12, 'l1-012', 1, 1, 0, 0, 0, 2, 0, 2, NOW()),
  (13, 'l1-013', 1, 1, 1, 0, 0, 0, 4, 0, NOW()),
  (14, 'l1-014', 1, 1, 0, 1, 0, 1, 2, 1, NOW()),
  (15, 'l1-015', 1, 1, 0, 1, 0, 2, 2, 0, NOW()),
  (16, 'l1-016', 1, 1, 0, 0, 1, 3, 1, 0, NOW()),
  (17, 'l1-017', 1, 2, 0, 2, 1, 0, 0, 0, NOW()),
  (18, 'l1-018', 1, 2, 0, 0, 0, 0, 3, 0, NOW()),
  (19, 'l1-019', 1, 2, 0, 1, 1, 0, 1, 1, NOW()),
  (20, 'l1-020', 1, 2, 0, 0, 2, 0, 2, 0, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (21, 'l1-021', 1, 2, 1, 0, 0, 0, 0, 4, NOW()),
  (22, 'l1-022', 1, 2, 0, 1, 1, 0, 1, 2, NOW()),
  (23, 'l1-023', 1, 2, 0, 0, 1, 0, 2, 2, NOW()),
  (24, 'l1-024', 1, 2, 0, 1, 3, 1, 0, 0, NOW()),
  (25, 'l1-025', 1, 3, 0, 0, 2, 1, 0, 0, NOW()),
  (26, 'l1-026', 1, 3, 0, 3, 0, 0, 0, 0, NOW()),
  (27, 'l1-027', 1, 3, 0, 1, 1, 1, 0, 1, NOW()),
  (28, 'l1-028', 1, 3, 0, 2, 0, 0, 2, 0, NOW()),
  (29, 'l1-029', 1, 3, 1, 4, 0, 0, 0, 0, NOW()),
  (30, 'l1-030', 1, 3, 0, 2, 1, 1, 0, 1, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (31, 'l1-031', 1, 3, 0, 2, 0, 1, 0, 2, NOW()),
  (32, 'l1-032', 1, 3, 0, 1, 0, 0, 1, 3, NOW()),
  (33, 'l1-033', 1, 4, 0, 0, 0, 2, 1, 0, NOW()),
  (34, 'l1-034', 1, 4, 0, 0, 0, 3, 0, 0, NOW()),
  (35, 'l1-035', 1, 4, 0, 1, 1, 1, 1, 0, NOW()),
  (36, 'l1-036', 1, 4, 0, 2, 0, 2, 0, 0, NOW()),
  (37, 'l1-037', 1, 4, 1, 0, 4, 0, 0, 0, NOW()),
  (38, 'l1-038', 1, 4, 0, 1, 2, 1, 1, 0, NOW()),
  (39, 'l1-039', 1, 4, 0, 2, 2, 0, 1, 0, NOW()),
  (40, 'l1-040', 1, 4, 0, 0, 0, 1, 3, 1, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (41, 'l2-001', 2, 0, 1, 0, 0, 3, 2, 2, NOW()),
  (42, 'l2-002', 2, 0, 1, 2, 3, 0, 3, 0, NOW()),
  (43, 'l2-003', 2, 0, 2, 0, 0, 1, 4, 2, NOW()),
  (44, 'l2-004', 2, 0, 2, 0, 0, 0, 5, 3, NOW()),
  (45, 'l2-005', 2, 0, 2, 0, 0, 0, 5, 0, NOW()),
  (46, 'l2-006', 2, 0, 3, 6, 0, 0, 0, 0, NOW()),
  (47, 'l2-007', 2, 1, 1, 0, 2, 2, 3, 0, NOW()),
  (48, 'l2-008', 2, 1, 1, 0, 2, 3, 0, 3, NOW()),
  (49, 'l2-009', 2, 1, 2, 5, 3, 0, 0, 0, NOW()),
  (50, 'l2-010', 2, 1, 2, 2, 0, 0, 1, 4, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (51, 'l2-011', 2, 1, 2, 0, 5, 0, 0, 0, NOW()),
  (52, 'l2-012', 2, 1, 3, 0, 6, 0, 0, 0, NOW()),
  (53, 'l2-013', 2, 2, 1, 2, 3, 0, 0, 2, NOW()),
  (54, 'l2-014', 2, 2, 1, 3, 0, 2, 3, 0, NOW()),
  (55, 'l2-015', 2, 2, 2, 4, 2, 0, 0, 1, NOW()),
  (56, 'l2-016', 2, 2, 2, 0, 5, 3, 0, 0, NOW()),
  (57, 'l2-017', 2, 2, 2, 0, 0, 5, 0, 0, NOW()),
  (58, 'l2-018', 2, 2, 3, 0, 0, 6, 0, 0, NOW()),
  (59, 'l2-019', 2, 3, 1, 2, 0, 0, 2, 3, NOW()),
  (60, 'l2-020', 2, 3, 1, 0, 3, 0, 2, 3, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (61, 'l2-021', 2, 3, 2, 1, 4, 2, 0, 0, NOW()),
  (62, 'l2-022', 2, 3, 2, 3, 0, 0, 0, 5, NOW()),
  (63, 'l2-023', 2, 3, 2, 0, 0, 0, 0, 5, NOW()),
  (64, 'l2-024', 2, 3, 3, 0, 0, 0, 6, 0, NOW()),
  (65, 'l2-025', 2, 4, 1, 3, 2, 2, 0, 0, NOW()),
  (66, 'l2-026', 2, 4, 1, 3, 0, 3, 0, 2, NOW()),
  (67, 'l2-027', 2, 4, 2, 0, 1, 4, 2, 0, NOW()),
  (68, 'l2-028', 2, 4, 2, 0, 0, 5, 3, 0, NOW()),
  (69, 'l2-029', 2, 4, 2, 5, 0, 0, 0, 0, NOW()),
  (70, 'l2-030', 2, 4, 3, 0, 0, 0, 0, 6, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (71, 'l3-001', 3, 0, 4, 0, 0, 0, 0, 7, NOW()),
  (72, 'l3-002', 3, 0, 5, 3, 0, 0, 0, 7, NOW()),
  (73, 'l3-003', 3, 0, 4, 3, 0, 0, 3, 6, NOW()),
  (74, 'l3-004', 3, 0, 3, 0, 3, 3, 5, 3, NOW()),
  (75, 'l3-005', 3, 1, 4, 7, 0, 0, 0, 0, NOW()),
  (76, 'l3-006', 3, 1, 5, 7, 3, 0, 0, 0, NOW()),
  (77, 'l3-007', 3, 1, 4, 6, 3, 0, 0, 3, NOW()),
  (78, 'l3-008', 3, 1, 3, 3, 0, 3, 3, 5, NOW()),
  (79, 'l3-009', 3, 2, 4, 0, 7, 0, 0, 0, NOW()),
  (80, 'l3-010', 3, 2, 5, 0, 7, 3, 0, 0, NOW());

INSERT INTO `splendor_card` (id, card_code, card_level, bonus, prestige, cost_white, cost_blue, cost_green, cost_red, cost_black, create_time) VALUES
  (81, 'l3-011', 3, 2, 4, 3, 6, 3, 0, 0, NOW()),
  (82, 'l3-012', 3, 2, 3, 5, 3, 0, 3, 3, NOW()),
  (83, 'l3-013', 3, 3, 4, 0, 0, 7, 0, 0, NOW()),
  (84, 'l3-014', 3, 3, 5, 0, 0, 7, 3, 0, NOW()),
  (85, 'l3-015', 3, 3, 4, 0, 3, 6, 3, 0, NOW()),
  (86, 'l3-016', 3, 3, 3, 3, 5, 3, 0, 3, NOW()),
  (87, 'l3-017', 3, 4, 4, 0, 0, 0, 7, 0, NOW()),
  (88, 'l3-018', 3, 4, 5, 0, 0, 0, 7, 3, NOW()),
  (89, 'l3-019', 3, 4, 4, 0, 0, 3, 6, 3, NOW()),
  (90, 'l3-020', 3, 4, 3, 3, 3, 5, 3, 0, NOW());

-- 3.3 宝石商人-贵族种子（10 张，由 game.yaml 生成，勿手改）
INSERT INTO `splendor_noble` (id, noble_code, noble_name, prestige, need_white, need_blue, need_green, need_red, need_black, create_time) VALUES
  (1, 'noble-01', NULL, 3, 4, 4, 0, 0, 0, NOW()),
  (2, 'noble-02', NULL, 3, 0, 4, 4, 0, 0, NOW()),
  (3, 'noble-03', NULL, 3, 0, 0, 4, 4, 0, NOW()),
  (4, 'noble-04', NULL, 3, 0, 0, 0, 4, 4, NOW()),
  (5, 'noble-05', NULL, 3, 4, 0, 0, 0, 4, NOW()),
  (6, 'noble-06', NULL, 3, 3, 3, 3, 0, 0, NOW()),
  (7, 'noble-07', NULL, 3, 3, 3, 0, 0, 3, NOW()),
  (8, 'noble-08', NULL, 3, 0, 3, 3, 3, 0, NOW()),
  (9, 'noble-09', NULL, 3, 0, 0, 3, 3, 3, NOW()),
  (10, 'noble-10', NULL, 3, 3, 0, 0, 3, 3, NOW());
