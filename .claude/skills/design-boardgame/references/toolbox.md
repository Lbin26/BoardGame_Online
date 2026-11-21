# 模拟工具箱用法

所有命令在 `tools/boardgame-design/` 目录内执行（`npm run sim -- <子命令> ...`）。

## validate — 校验游戏/样本文档

```bash
npm run sim -- validate ../../design/samples/splendor.yaml
npm run sim -- validate ../../design/games/myGame/game.yaml
```

成功输出 `OK <file>`（exit 0）；失败逐行打印错误（exit 1）。校验内容：schema 形状、机制 key ∈ 词汇表、经济资源交叉引用。

## 配置驱动的模拟（证据级，报告落盘）

sim 配置 YAML（放在设计工作区 `sims/` 下）：

```yaml
game: ../game.yaml          # 相对本配置文件的路径；game.yaml 是数值单一事实来源
sim: economy                # dice | cards | economy | conflict
from: subsystems.economy    # 点路径取子系统（支持数组下标，如 components.dice.0）
seed: 42                    # 缺省 42
runs: 10000                 # 蒙特卡洛局数，缺省 10000
overrides:                  # 深合并：对象递归、数组整体替换
  turns: 20                 # economy 运行参数（不在 game.yaml schema 内）
  policy: { type: greedy }  # greedy | priority（须给 order: [actionKey...]）| static（静态投影，见下）
out: report.md              # 可选；缺省 <配置目录>/<sim>-report.md
```

```bash
npm run sim -- dice --config sims/dice.yaml
npm run sim -- cards --config sims/cards.yaml
npm run sim -- economy --config sims/economy.yaml
npm run sim -- conflict --config sims/conflict.yaml
npm run sim -- sweep --config sims/sweep.yaml
```

输出 md 报告 + 同名 json 原始数据（不含时间戳，逐位可复现）。stdout 打印报告路径与顶层标量。

### 各模拟器参数要点

- **dice**：from 指向具体骰子条目（`components.dice.0`），overrides 补 `symbol`/`op`/`value`。结果含 exact（精确卷积分布 pmf/probability/mean）与 mc（蒙特卡洛交叉验证）。
- **cards**：`from: components.decks.N` 整取牌库条目（含 `cards`），模拟自动把 `cards→deck`（`{key,count}` 投影，单一事实来源，无需手抄）；再 overrides 补 `draw`/`target`/`op`/`value`。也可在 overrides 直接给 `deck: [{key,count}]`。精确超几何概率 + 期望张数 + 首次抽到目标的期望抽数。
- **economy**：两层，由 `policy.type` 选择。**静态投影** `policy: { type: static }` → 看 netFlow/noSink（有产出无沉没口的资源），不跑行动；**策略模拟** `greedy`/`priority` → 看 stalled/stallFromTurn（连续 stallK 回合无可负担行动）。policy 是声明式参数，不是 AI。护栏：资源值须非负；自维持行动（cost 为空或 gain 逐资源 ≥ cost）与行动闭环互喂会被拒绝（防死循环）；priority.order 含未知行动键会报错（防假 stall）。
- **conflict**：from `subsystems.conflict`，双方 `{diceCount, faces, hitSymbols, hp, bonus?}`；伤害同时结算，双亡/超时为平局。

### sweep — 参数扫描

```yaml
game: ../game.yaml
sim: conflict
from: subsystems.conflict
seed: 42
runs: 5000
sweep:
  param: attacker.diceCount   # 对解析后 params 的点路径逐值覆盖
  values: [2, 3, 4]
```

每值同种子保证可比；输出 `sweep-report.md/json`（result.rows）。基础 params 不被污染。

## ad-hoc 直跑（仅探索，非证据）

```bash
npm run sim -- dice --faces sword,sword,shield,blank,blank,blank --count 5 --symbol sword --op ">=" --value 3
npm run sim -- cards --deck silk:8,other:32 --draw 5 --target silk --op ">=" --value 1
```

stdout 首行固定 `AD-HOC（非证据）`，不写报告文件。**平衡论断不得引用 ad-hoc 输出**——要证据就写配置跑落盘报告。纯数字面自动转 number。

## 报告解读

- md 含配置回显（yaml）、种子/局数、顶层标量表、复杂结果的 yaml 块。
- json 的 `{config, params, result}` 是机器可读原始数据，供后续分析。
- 引用报告时给出相对仓库根的路径（如 `design/games/myGame/sims/economy-report.md`）。

## 退出码

0 成功；1 参数/校验错误；2 文件不存在。