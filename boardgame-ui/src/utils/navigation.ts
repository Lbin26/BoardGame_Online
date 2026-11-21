import type { Router } from 'vue-router'

/**
 * 返回上一页；无浏览器历史（直接打开深链）时回退到大厅。
 * 供对局页/规则页「返回上一页」按钮共用，避免 back() 退到站外空页。
 */
export function goBackOrLobby(router: Router, fallback = '/lobby'): void {
  if (window.history.length > 1) router.back()
  else router.push(fallback)
}
