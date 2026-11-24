/** 平台超级管理员角色名（与后端 sys_role.role_name / role_key='superadmin' 对应） */
export const SUPER_ADMIN_ROLE_NAME = '超级管理员'

/**
 * 是否平台超级管理员。
 *
 * 以个人资料接口返回的 roleGroup 为准（数据库角色表），与后端公告发布接口的
 * superadmin 角色判定保持一致；不再按用户名/昵称硬编码白名单。
 */
export function isSuperAdmin(roleGroup?: string | null): boolean {
  return (roleGroup ?? '')
    .split(',')
    .map((name) => name.trim())
    .includes(SUPER_ADMIN_ROLE_NAME)
}
