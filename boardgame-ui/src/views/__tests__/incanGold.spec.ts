import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  route: { params: { roomId: 'ABC123' } as Record<string, string> },
  store: {
    isLogin: true,
    userInfo: { userName: 'bob' } as { userName: string } | null,
    fetchProfile: vi.fn(),
  },
  send: vi.fn(() => true),
  // 模板会 unref，mock 需带 __v_isRef 才能被正确解包
  fakeRef: (v: any) => ({ value: v, __v_isRef: true }),
  onMessage: null as ((msg: any) => void) | null,
  roomData: { success: true, room: null as any },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace, go: vi.fn() }),
  useRoute: () => mocks.route,
}))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/incanGold', () => ({
  getRoom: vi.fn(async () => mocks.roomData),
  listRooms: vi.fn(async () => ({ rooms: [] })),
  createRoom: vi.fn(),
  takeSeat: vi.fn(async () => ({ success: true })),
  standUp: vi.fn(async () => ({ success: true })),
}))
vi.mock('@/composables/useGameWebSocket', () => ({
  useGameWebSocket: (opts: any) => {
    mocks.onMessage = opts.onMessage
    return {
      connected: mocks.fakeRef(true),
      replacedNotice: mocks.fakeRef(false),
      send: mocks.send,
      connect: vi.fn(),
      markServerClosed: vi.fn(),
    }
  },
}))

import IncanGold from '@/views/incanGold/index.vue'

enableAutoUnmount(afterEach)

const waitingRoom = {
  roomId: 'ABC123',
  hostName: 'bob',
  maxPlayers: 4,
  status: 'WAITING',
  mySeatNo: 1,
  seats: [
    { seatNo: 1, name: 'bob', online: true, afk: false },
    { seatNo: 2, name: 'alice', online: true, afk: false },
    { seatNo: 3, name: 'carol', online: true, afk: false },
  ],
  afkPlayers: [],
}

const playingRoom = {
  ...waitingRoom,
  status: 'PLAYING',
  seats: [
    { seatNo: 1, name: 'alice', online: true, afk: false },
    { seatNo: 2, name: 'bob', online: true, afk: false },
    { seatNo: 3, name: 'carol', online: true, afk: false },
  ],
  mySeatNo: 2,
}

function decisionSnapshot(overrides: Record<string, any> = {}) {
  return {
    type: 'STATE_SYNC',
    payload: {
      phase: 'DECIDE',
      round: 2,
      endCause: null,
      phaseDeadlineAt: Date.now() + 10_000,
      field: [
        { kind: 'TREASURE', value: 10, hazard: null, remainder: 1 },
        { kind: 'TREASURE', value: 4, hazard: null, remainder: 0 },
        { kind: 'HAZARD', value: 0, hazard: 'fire', remainder: 0 },
        { kind: 'ARTIFACT', value: 3, hazard: null, remainder: 0 },
      ],
      fieldArtifacts: 1,
      hazardRemaining: { fire: 2, mummy: 3, rock: 3, snake: 3, spider: 3 },
      players: [
        { seatNo: 1, name: 'alice', online: true, afk: false, inExpedition: true, submitted: false, campGems: null, roundGems: null, artifacts: 0 },
        { seatNo: 2, name: 'bob', online: true, afk: false, inExpedition: true, submitted: false, campGems: 7, roundGems: 3, artifacts: 1 },
        { seatNo: 3, name: 'carol', online: true, afk: false, inExpedition: false, submitted: true, campGems: null, roundGems: null, artifacts: 0 },
      ],
      events: [{ seq: 12, code: 'CHOICE_MADE', message: 'carol 已提交决策', data: {} }],
      rankings: [],
      ...overrides,
    },
  }
}

describe('印加宝藏对局页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.roomData.room = waitingRoom
    mocks.onMessage = null
  })

  it('等待区：房主点击开始探险发出 START_GAME', async () => {
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(w.text()).toContain('探险营地')

    const start = w.findAll('button').find((b) => b.text().includes('开始探险'))
    expect(start, '房主应看到开始按钮').toBeTruthy()
    await start!.trigger('click')
    expect(mocks.send).toHaveBeenCalledWith({ type: 'START_GAME', payload: {} })
  })

  it('人数不足时房主无法开始', async () => {
    mocks.roomData.room = { ...waitingRoom, seats: waitingRoom.seats.slice(0, 2) }
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const start = w.findAll('button').find((b) => b.text().includes('开始探险'))
    expect(start!.attributes('disabled')).toBeDefined()
  })

  it('DECIDE：显示桌面牌与两个暗选按钮，点撤退发出 CHOICE', async () => {
    mocks.roomData.room = playingRoom
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(decisionSnapshot())
    await flushPromises()

    expect(w.text()).toContain('第 2 / 5 轮')
    expect(w.text()).toContain('神庙桌面')
    expect(w.text()).toContain('10 · 剩余 1')
    expect(w.text()).toContain('4 · 剩余 0')
    const choices = w.findAll('.choice')
    expect(choices.length).toBe(2)
    await choices[1].trigger('click')
    expect(mocks.send).toHaveBeenCalledWith({ type: 'CHOICE', payload: { choice: 'RETREAT' } })
  })

  it('倒计时在轮数下方，最后 5 秒切换紧急警示', async () => {
    mocks.roomData.room = playingRoom
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(decisionSnapshot({ phaseDeadlineAt: Date.now() + 10_000 }))
    await flushPromises()

    const bar = w.find('.status-bar')
    const main = bar.find('.status-main')
    const alert = w.find('.status-bar .countdown-alert')
    expect(alert.exists()).toBe(true)
    expect(bar.element.children[1], '倒计时应紧跟在轮数行之后').toBe(alert.element)
    expect(alert.text()).toContain('秘密决策倒计时')
    expect(alert.text()).toMatch(/\d+秒/)
    expect(alert.classes()).not.toContain('urgent')

    mocks.onMessage!(decisionSnapshot({ phaseDeadlineAt: Date.now() + 3_000 }))
    await flushPromises()
    expect(w.find('.countdown-alert').classes()).toContain('urgent')
    expect(w.find('.countdown-alert').text()).toContain('最后 5 秒')
    expect(main.text()).toContain('第 2 / 5 轮')
  })

  it('页眉与其他游戏同构：房间号/复制、连接/规则、返回/回到大厅/解散', async () => {
    mocks.roomData.room = waitingRoom
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const header = w.find('header.room-bar')
    expect(header.exists()).toBe(true)
    expect(header.find('.bar-left').text()).toContain('ABC123')
    expect(header.find('.bar-center').text()).toContain('📖 规则')
    expect(header.find('.bar-right').text()).toContain('回到大厅')
    expect(header.find('.bar-right').text()).toContain('解散')
    expect(header.findAll('button').some((b) => b.text().includes('复制'))).toBe(true)
  })

  it('只有本人宝石可见，他人显示帐篷遮挡', async () => {
    mocks.roomData.room = playingRoom
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(decisionSnapshot())
    await flushPromises()

    expect(w.text()).toContain('营地 7')
    expect(w.text()).toContain('本轮 3')
    expect(w.text()).toContain('帐篷遮挡')
  })

  it('提交后按钮消失并显示等待文案', async () => {
    mocks.roomData.room = playingRoom
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(decisionSnapshot())
    await flushPromises()

    await w.findAll('.choice')[0].trigger('click')
    await flushPromises()
    expect(w.text()).toContain('已提交继续深入')
    expect(w.findAll('.choice').length).toBe(0)
  })

  it('团灭结算：展示原因与房主的下一轮按钮', async () => {
    mocks.roomData.room = playingRoom
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(decisionSnapshot({ phase: 'ROUND_END', endCause: 'COLLAPSE' }))
    await flushPromises()

    expect(w.text()).toContain('灾难团灭')
    const next = w.findAll('button').find((b) => b.text().includes('进入下一轮'))
    expect(next).toBeTruthy()
    await next!.trigger('click')
    expect(mocks.send).toHaveBeenCalledWith({ type: 'NEXT_ROUND', payload: {} })
  })

  it('终局：GAME_OVER 排名按分数展示', async () => {
    mocks.roomData.room = playingRoom
    const w = mount(IncanGold, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(decisionSnapshot({ phase: 'FINISHED' }))
    mocks.onMessage!({
      type: 'GAME_OVER',
      payload: {
        rankings: [
          { seatNo: 2, name: 'bob', gems: 15, artifacts: 2, score: 25 },
          { seatNo: 1, name: 'alice', gems: 20, artifacts: 0, score: 20 },
        ],
      },
    })
    await flushPromises()

    expect(w.text()).toContain('最终排名')
    expect(w.text()).toContain('25 分')
  })
})
