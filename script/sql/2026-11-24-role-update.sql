-- ------------------------------------------------------------
-- 角色身份表更新：全平台统一为「普通用户」，「欧洲小跪族」为超级管理员
-- 执行库：boardgame_online（与 ry_vue.sql 同库）
-- 幂等：可重复执行；以 role_key = 'common' / 'superadmin' 定位角色，不依赖雪花 ID
-- ------------------------------------------------------------

-- 1. 新增普通用户角色（不存在才插入）
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

-- 2. 清理演示角色（本部门及以下 / 仅本人），平台不再使用
UPDATE sys_role SET del_flag = '1', update_time = NOW()
WHERE role_key IN ('test1', 'test2') AND del_flag = '0';

-- 3. 清空所有在用用户的角色关联，再按统一口径重建
DELETE FROM sys_user_role
WHERE user_id IN (SELECT user_id FROM sys_user WHERE del_flag = '0');

-- 4. 普通用户：除超管外的全部在用账号
INSERT INTO sys_user_role (user_id, role_id)
SELECT user_id, @common_role_id
FROM sys_user
WHERE del_flag = '0' AND user_name <> '欧洲小跪族' AND nick_name <> '欧洲小跪族';

-- 5. 超级管理员：唯一账号「欧洲小跪族」（userName / nickName 任一命中）
INSERT INTO sys_user_role (user_id, role_id)
SELECT user_id, @super_role_id
FROM sys_user
WHERE del_flag = '0' AND (user_name = '欧洲小跪族' OR nick_name = '欧洲小跪族');

-- 6. 自检：应为每个在用用户恰好一条角色，且超管只有一人
-- SELECT u.user_name, r.role_name FROM sys_user u
--   JOIN sys_user_role ur ON ur.user_id = u.user_id
--   JOIN sys_role r ON r.role_id = ur.role_id
-- WHERE u.del_flag = '0' ORDER BY r.role_key, u.user_name;
