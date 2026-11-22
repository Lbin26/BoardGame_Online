/**
 * 36 名选手展示目录（M1：编号色块 + 中文名 + 能力摘要占位素材）。
 * key/name/abilityType/abilitySummary 与后端 RacerCatalog 及 game.yaml 一一对应。
 * M6 再用 game-art-prompt-pack + ImageGen 批量出头像，替换色块即可，key 不变。
 */
import type { RacerInfo } from '@/api/magicalAthlete'

export const RACERS: Record<string, RacerInfo> = {
  alchemist: { key: 'alchemist', name: '炼金术士', abilityType: 'fixed-move', abilitySummary: '主骰1/2可把移动基数替换为4，再加修正' },
  baba_yaga: { key: 'baba_yaga', name: '鸡脚屋', abilityType: 'interrupt', abilitySummary: '单独停入或被停入时令对方绊倒' },
  banana: { key: 'banana', name: '香蕉', abilityType: 'interrupt', abilitySummary: '被一次移动越过时令移动者绊倒，含反向越过' },
  blimp: { key: 'blimp', name: '飞艇', abilityType: 'mod-move', abilitySummary: '回合起点在第二角前主移动+3，否则−1' },
  centaur: { key: 'centaur', name: '半人马', abilityType: 'interrupt', abilitySummary: '超越时对方后退2格，后退越过也触发' },
  cheerleader: { key: 'cheerleader', name: '啦啦队长', abilityType: 'mod-move', abilitySummary: '可令所有最后名同时+2，然后自己+1' },
  coach: { key: 'coach', name: '教练', abilityType: 'mod-move', abilitySummary: '所在格全部运动员主移动+1' },
  copycat: { key: 'copycat', name: '模仿猫', abilityType: 'copy', abilitySummary: '持续复制当前领先者能力，不复制赛前能力' },
  dicemonger: { key: 'dicemonger', name: '骰子商人', abilityType: 'reactive', abilitySummary: '每人每回合可额外重掷一次；别人重掷时自己+1' },
  duelist: { key: 'duelist', name: '决斗家', abilityType: 'interrupt', abilitySummary: '同格可决斗，比骰高者+2' },
  egg: { key: 'egg', name: '蛋蛋', abilityType: 'copy', abilitySummary: '赛前抽三张选一复制其能力' },
  flip_flop: { key: 'flip_flop', name: '人字拖', abilityType: 'warp', abilitySummary: '可用与另一选手交换位置替代主移动' },
  genius: { key: 'genius', name: '天才', abilityType: 'reactive', abilitySummary: '猜中最终主骰点数获得额外回合' },
  gunk: { key: 'gunk', name: '黏液怪', abilityType: 'mod-move', abilitySummary: '其他选手主移动−1，多个叠加' },
  hare: { key: 'hare', name: '野兔', abilityType: 'mod-move', abilitySummary: '主移动+2；独自领先则跳过主移动；不发铜分' },
  heckler: { key: 'heckler', name: '起哄者', abilityType: 'reactive', abilitySummary: '任一选手回合结束位移≤1时自己+2' },
  huge_baby: { key: 'huge_baby', name: '巨婴', abilityType: 'interrupt', abilitySummary: '非起点禁止共格，把后来者放到其后一格' },
  hypnotist: { key: 'hypnotist', name: '催眠师', abilityType: 'warp', abilitySummary: '回合初可传送一名选手到自己所在格' },
  inchworm: { key: 'inchworm', name: '尺蠖', abilityType: 'interrupt', abilitySummary: '别人最终主骰为1时取消其主移动，自己+1' },
  lackey: { key: 'lackey', name: '跟班', abilityType: 'reactive', abilitySummary: '别人最终主骰为6时自己先+2' },
  leaptoad: { key: 'leaptoad', name: '跳跳蟾蜍', abilityType: 'interrupt', abilitySummary: '移动只计空格，每跳过一个占据格算一次发动' },
  legs: { key: 'legs', name: '大长腿', abilityType: 'fixed-move', abilitySummary: '可不掷骰，主移动改为5，仍受修正' },
  lovable_loser: { key: 'lovable_loser', name: '可爱的倒霉蛋', abilityType: 'scoring', abilitySummary: '回合初独自垫底获得1分' },
  magician: { key: 'magician', name: '魔术师', abilityType: 'reactive', abilitySummary: '主骰最多重掷两次，使用最终结果' },
  mastermind: { key: 'mastermind', name: '幕后军师', abilityType: 'scoring', abilitySummary: '首回合初预测冠军，猜中立即获第二并结束比赛' },
  mouth: { key: 'mouth', name: '大嘴怪', abilityType: 'interrupt', abilitySummary: '停入恰有一名对手的格时淘汰该对手' },
  party_animal: { key: 'party_animal', name: '派对动物', abilityType: 'mod-move', abilitySummary: '回合初其他人同时向自己移动1；同格每人使主移动+1' },
  rocket_scientist: { key: 'rocket_scientist', name: '火箭科学家', abilityType: 'mod-move', abilitySummary: '主骰可翻倍再加修正，完成后自己绊倒' },
  romantic: { key: 'romantic', name: '浪漫家', abilityType: 'reactive', abilitySummary: '有人停入恰有一人的格时自己+2' },
  scoocher: { key: 'scoocher', name: '蹭蹭怪', abilityType: 'reactive', abilitySummary: '别人能力每次实际发动自己+1，不限每回合一次' },
  sisyphus: { key: 'sisyphus', name: '西西弗斯', abilityType: 'scoring', abilitySummary: '赛前+4分；主骰为6传送回起点并−1分' },
  skipper: { key: 'skipper', name: '船长', abilityType: 'interrupt', abilitySummary: '任一最终主骰为1时下一个行动者改为自己' },
  stickler: { key: 'stickler', name: '较真怪', abilityType: 'interrupt', abilitySummary: '别人所有移动必须恰好冲线，过量则整次取消' },
  suckerfish: { key: 'suckerfish', name: '吸盘鱼', abilityType: 'warp', abilitySummary: '开始移动前选择跟随，同组同时到达' },
  third_wheel: { key: 'third_wheel', name: '电灯泡', abilityType: 'warp', abilitySummary: '回合初可传送至恰有两人的格，之后仍有主移动' },
  twin: { key: 'twin', name: '双胞胎', abilityType: 'copy', abilitySummary: '赛前可复制上一场冠军能力' },
}

const TYPE_LABELS: Record<string, string> = {
  'fixed-move': '定点移动',
  'mod-move': '移动修正',
  interrupt: '打断',
  reactive: '反应',
  scoring: '得分',
  warp: '传送',
  copy: '复制',
}

/** 展示名兜底：未知 key 直接回显 */
export function racerName(key: string): string {
  return RACERS[key]?.name ?? key
}

export function racerInfo(key: string): RacerInfo {
  return RACERS[key] ?? { key, name: key, abilityType: '', abilitySummary: '' }
}

export function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type
}

/** 色块占位风格：按 key 稳定映射到一个色相 */
export function racerHue(key: string): number {
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) % 360
  return h
}
