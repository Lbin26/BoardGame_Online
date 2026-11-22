import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// 捕获 useGameWebSocket 注入的 onMessage，用于模拟服务端推送
const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  go: vi.fn(),
  route: { params: { roomId: 'ABC123' } as Record<string, string> },
  store: { isLogin: true, userInfo: { userName: 'bob' } as { userName: string } | null, fetchProfile: vi.fn() },
  send: vi.fn(),
  onMessage: null as ((msg: any) => void) | null,
  roomData: { success: true, room: null as any },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace, go: mocks.go }),
  useRoute: () => mocks.route,
}))
vi.mock('@/store/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/api/magicalAthlete', () => ({
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
      connected: { value: true },
      replacedNotice: { value: false },
      send: mocks.send,
      connect: vi.fn(),
      markServerClosed: vi.fn(),
    }
  },
}))

import MagicalAthlete from '@/views/magicalAthlete/index.vue'

enableAutoUnmount(afterEach)

const room = {
  roomId: 'ABC123',
  hostName: 'alice',
  maxPlayers: 4,
  status: 'PLAYING',
  mySeatNo: 2,
  seats: [
    { seatNo: 1, name: 'alice', online: true, afk: false },
    { seatNo: 2, name: 'bob', online: true, afk: false },
    { seatNo: 3, name: 'carol', online: true, afk: false },
  ],
}

function draftSnapshot(currentSeatNo: number) {
  return {
    type: 'STATE_SYNC',
    payload: {
      phase: 'DRAFT',
      raceNo: 0,
      batch: 0,
      pickOrder: [1, 2, 3],
      pickIndex: currentSeatNo === 2 ? 1 : 0,
      pool: ['hare', 'gunk', 'blimp'],
      hands: { 1: [], 2: [], 3: [] },
      currentSeatNo,
      deadlineAt: Date.now() + 30000,
      finished: false,
      players: [
        { seatNo: 1, name: 'alice', online: true, afk: false },
        { seatNo: 2, name: 'bob', online: true, afk: false },
        { seatNo: 3, name: 'carol', online: true, afk: false },
      ],
      readySeats: [],
      activeRacerKeys: {},
      myActiveRacerKey: null,
      racers: [],
      lastDice: null,
      diceSeatNo: null,
      lastEffects: [],
      goldSeat: null,
      silverSeat: null,
      scores: {},
      rankings: [],
      myOptionalOptions: [],
    },
  }
}

describe('胡闹运动会选人 bug 复现', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.roomData.room = room
    mocks.onMessage = null
  })

  it('DRAFT：轮到我(座位2)时，racer-card 应可点击并发送 PICK_RACER', async () => {
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(mocks.onMessage).not.toBeNull()
    mocks.onMessage!(draftSnapshot(2))
    await flushPromises()

    const cards = w.findAll('.racer-card')
    expect(cards.length).toBe(3)
    const first = cards[0]
    const disabled = first.attributes('disabled')
    expect(disabled, '轮到我时卡片不应 disabled').toBeUndefined()

    await first.trigger('click')
    expect(mocks.send).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'PICK_RACER', payload: { racerKey: 'hare' } }),
    )
  })

  function musterSnapshot() {
    return {
      type: 'STATE_SYNC',
      payload: {
        ...draftSnapshot(0).payload,
        phase: 'MUSTER',
        raceNo: 1,
        pickIndex: 0,
        currentSeatNo: null,
        // 服务端 MUSTER 只下发 viewer 自己的手牌（座位2）
        hands: { 2: ['hare', 'gunk', 'blimp'] },
        myActiveRacerKey: null,
      },
    }
  }

  it('DRAFT：座位号为字符串 "2" 时仍能识别轮到我并可选人（回归 bug）', async () => {
    const snap = draftSnapshot(2)
    ;(snap.payload as any).currentSeatNo = '2' // 服务端某序列化路径下发字符串
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(snap)
    await flushPromises()

    const cards = w.findAll('.racer-card')
    expect(cards[0].attributes('disabled'), '字符串座位号也应可点').toBeUndefined()
    await cards[0].trigger('click')
    expect(mocks.send).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'PICK_RACER', payload: { racerKey: 'hare' } }),
    )
  })

  it('MUSTER：暗选阶段应显示我的手牌并可点击锁定', async () => {
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(musterSnapshot())
    await flushPromises()

    const cards = w.findAll('.racer-card')
    expect(cards.length, '应显示本人 3 张手牌').toBe(3)
    await cards[0].trigger('click')
    expect(mocks.send).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'READY_RACER', payload: { racerKey: 'hare' } }),
    )
  })

  it('DRAFT：快照先于房间信息到达（room 仍为 null）时，座位应能补反查', async () => {
    // 让 getRoom 延迟返回，模拟快照先到的竞争
    let resolveRoom: (v: any) => void = () => {}
    const delayedRoom = new Promise((res) => { resolveRoom = res })
    ;(mocks.roomData as any).room = room
    const apiMod = await import('@/api/magicalAthlete')
    vi.mocked(apiMod.getRoom).mockImplementationOnce(() => delayedRoom as any)

    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    // 此时 room 还没就绪，先推 DRAFT 快照
    mocks.onMessage!(draftSnapshot(2))
    await flushPromises()
    // 房间信息后到
    resolveRoom(mocks.roomData)
    await flushPromises()
    await flushPromises()

    const cards = w.findAll('.racer-card')
    if (cards.length === 3) {
      expect(cards[0].attributes('disabled'), '快照先到后仍应可点').toBeUndefined()
    }
  })

  it('DRAFT：room.mySeatNo 被中立广播清空后，仍能按名字反查座位并选人', async () => {
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    // 模拟中立 SEAT_CHANGED：room.mySeatNo = null
    mocks.onMessage!({
      type: 'SEAT_CHANGED',
      payload: { room: { ...room, mySeatNo: null }, seatNo: 2, playerName: 'bob' },
    })
    await flushPromises()
    mocks.onMessage!(draftSnapshot(2))
    await flushPromises()

    const cards = w.findAll('.racer-card')
    expect(cards.length).toBe(3)
    expect(cards[0].attributes('disabled'), '仍应可点击').toBeUndefined()
  })
})

describe('M5 3 人双角色变体前端适配', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.roomData.room = room
    mocks.onMessage = null
  })

  /** 房主为本人（bob）、恰好 3 人入座、等待开局 */
  function waitingRoom() {
    return { ...room, hostName: 'bob', status: 'WAITING' as const }
  }

  /** 双角色局 MUSTER 快照：keys 为本人已暗选的选手（顺序即出场顺序） */
  function doubleMuster(keys: string[]) {
    const snap = draftSnapshot(0)
    const p = snap.payload as any
    p.phase = 'MUSTER'
    p.raceNo = 1
    p.currentSeatNo = null
    p.hands = { 2: ['hare', 'gunk', 'blimp'] }
    p.myActiveRacerKey = keys[0] ?? null
    p.myActiveRacerKeys = keys
    p.variant = 'THREE_DOUBLE'
    p.racersPerSeat = 2
    p.musterProgress = { 1: 0, 2: keys.length, 3: 0 }
    p.readySeats = keys.length >= 2 ? [2] : []
    return snap
  }

  /** 双角色局 RACE 快照：6 个 lane，座位 2 对应 lane 3/4 */
  function doubleRace() {
    const snap = draftSnapshot(0)
    const p = snap.payload as any
    p.phase = 'RACE'
    p.raceNo = 1
    p.currentSeatNo = 2
    p.currentLane = 3
    p.variant = 'THREE_DOUBLE'
    p.racersPerSeat = 2
    p.myActiveRacerKey = null
    p.myActiveRacerKeys = []
    p.myOptionalOptions = ['TARGET_SEAT']
    p.scores = { 1: 0, 2: 0, 3: 0 }
    p.lastDice = null
    p.diceSeatNo = null
    p.activeRacerKeysByLane = {
      1: 'hare', 2: 'gunk', 3: 'blimp', 4: 'leaptoad', 5: 'legs', 6: 'genius',
    }
    p.laneToSeat = { 1: 1, 2: 1, 3: 2, 4: 2, 5: 3, 6: 3 }
    const names = ['alice', 'alice', 'bob', 'bob', 'carol', 'carol']
    p.racers = [1, 2, 3, 4, 5, 6].map((lane) => ({
      seatNo: lane,
      name: names[lane - 1],
      racerKey: p.activeRacerKeysByLane[String(lane)],
      position: 0,
      tripped: false,
      finishRank: 0,
      bronze: 0,
      ownerSeatNo: p.laneToSeat[String(lane)],
    }))
    return snap
  }

  it('WAITING：恰好 3 人入座时房主可见变体开关，勾选后开局携带 THREE_DOUBLE', async () => {
    mocks.roomData.room = waitingRoom()
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const toggle = w.find('.variant-toggle input')
    expect(toggle.exists(), '3 人局应显示双角色变体开关').toBe(true)
    await toggle.setValue(true)

    const startBtn = w.findAll('.waiting-ops button').find((b) => b.text().includes('开局'))
    expect(startBtn, '应存在开局按钮').toBeTruthy()
    await startBtn!.trigger('click')
    expect(mocks.send).toHaveBeenCalledWith({
      type: 'START_GAME',
      payload: { variant: 'THREE_DOUBLE' },
    })
  })

  it('WAITING：非 3 人局不显示变体开关，开局不带 variant 参数', async () => {
    mocks.roomData.room = {
      ...waitingRoom(),
      seats: [
        { seatNo: 1, name: 'alice', online: true, afk: false },
        { seatNo: 2, name: 'bob', online: true, afk: false },
        { seatNo: 3, name: null, online: false, afk: false },
      ],
    }
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(w.find('.variant-toggle').exists(), '2 人局不应提供双角色开关').toBe(false)
    const startBtn = w.findAll('.waiting-ops button').find((b) => b.text().includes('开局'))
    await startBtn!.trigger('click')
    expect(mocks.send).toHaveBeenCalledWith({ type: 'START_GAME', payload: {} })
  })

  it('DRAFT：双角色局轮抽批次显示为 4 批', async () => {
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const snap = draftSnapshot(2)
    ;(snap.payload as any).variant = 'THREE_DOUBLE'
    ;(snap.payload as any).racersPerSeat = 2
    mocks.onMessage!(snap)
    await flushPromises()

    expect(w.text()).toContain('/4 批')
  })

  it('MUSTER 双角色：已选 1 名时仍可点第二张，选满后手牌区隐藏', async () => {
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(doubleMuster(['hare']))
    await flushPromises()

    expect(w.text(), '应显示本人已锁定的出场顺序').toContain('出场 1 · 野兔')
    const cards = w.findAll('.racer-card')
    expect(cards.length).toBe(3)
    expect(cards[0].attributes('disabled'), '已锁定的选手不可重复点选').toBeDefined()
    await cards[1].trigger('click')
    expect(mocks.send).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'READY_RACER', payload: { racerKey: 'gunk' } }),
    )

    // 选满 2 名后 musterDone=true，手牌区整体隐藏
    mocks.onMessage!(doubleMuster(['hare', 'gunk']))
    await flushPromises()
    expect(w.findAll('.racer-card').length, '选满后不应再出现手牌卡').toBe(0)
    expect(w.text()).toContain('出场 2 · 黏液怪')
  })

  it('RACE 双角色：棋子按 ownerSeatNo 高亮，TARGET_SEAT 用选手下拉提交 lane', async () => {
    const w = mount(MagicalAthlete, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mocks.onMessage!(doubleRace())
    await flushPromises()

    expect(w.findAll('.piece.mine').length, '座位 2 的两名选手都应高亮').toBe(2)
    expect(w.text(), '应提示当前出场选手').toContain('当前出场：飞艇')

    const select = w.find('.optional-panel select')
    expect(select.exists(), '双角色局 TARGET_SEAT 应改用选手下拉').toBe(true)
    await select.setValue(4)
    await w.find('.optional-btn').trigger('click')
    expect(mocks.send).toHaveBeenCalledWith({
      type: 'USE_OPTIONAL',
      payload: { usePrimary: false, targetSeat: 4, targetSpace: 0, guessDice: 0 },
    })
  })
})
