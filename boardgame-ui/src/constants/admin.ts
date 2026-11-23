/** 平台超级管理员名单（用户指定：“欧洲小跪族”）。按 userName 或 nickName 匹配，二者任一命中即为管理员。 */
export const SUPER_ADMIN_NAMES = ['欧洲小跪族'] as const

/** 是否平台超级管理员 */
export function isSuperAdmin(userName?: string | null, nickName?: string | null): boolean {
  const names = [userName, nickName].filter((v): v is string => !!v)
  return names.some((name) => (SUPER_ADMIN_NAMES as readonly string[]).includes(name))
}
