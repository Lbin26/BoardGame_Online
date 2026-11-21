/** 工作台预置：sampleKey 对应样本 meta.key；demo=true 的预置为演示数据，非样本数值 */
export interface Preset {
  key: string
  label: string
  sampleKey: string
  configText: string
  demo: boolean
}

const DEMO_HEADER = '# 演示数据，非样本数值（本样本无可模拟子系统，overrides 注入演示骰子参数）\n'

function demoConfig(file: string): string {
  return (
    DEMO_HEADER +
    `game: ${file}\nsim: dice\nfrom: subsystems.scoring\nseed: 42\nruns: 10000\n` +
    "overrides:\n  count: 2\n  faces: [1, 2, 3, 4, 5, 6]\n  symbol: 6\n  op: '>='\n  value: 10\n"
  )
}

export const PRESETS: Preset[] = [
  {
    key: 'splendor',
    label: '璀璨宝石 · 经济',
    sampleKey: 'splendor',
    demo: false,
    configText: 'game: splendor.yaml\nsim: economy\nfrom: subsystems.economy\nseed: 42\nruns: 1000\noverrides:\n  turns: 20\n',
  },
  {
    key: 'brass',
    label: '伯明翰 · 经济',
    sampleKey: 'brass',
    demo: false,
    configText: 'game: brass.yaml\nsim: economy\nfrom: subsystems.economy\nseed: 42\nruns: 1000\noverrides:\n  turns: 20\n',
  },
  {
    key: 'diceThrone',
    label: '王权骰铸 · 战斗',
    sampleKey: 'diceThrone',
    demo: false,
    configText: 'game: diceThrone.yaml\nsim: conflict\nfrom: subsystems.conflict\nseed: 42\nruns: 10000\n',
  },
  { key: 'xiangqi', label: '中国象棋 · 演示骰子', sampleKey: 'xiangqi', demo: true, configText: demoConfig('xiangqi.yaml') },
  { key: 'go', label: '围棋 · 演示骰子', sampleKey: 'go', demo: true, configText: demoConfig('go.yaml') },
  { key: 'gomoku', label: '五子棋 · 演示骰子', sampleKey: 'gomoku', demo: true, configText: demoConfig('gomoku.yaml') },
  { key: 'avalon', label: '阿瓦隆 · 演示骰子', sampleKey: 'avalon', demo: true, configText: demoConfig('avalon.yaml') },
]