# 机制库

与 `tools/boardgame-design/data/mechanisms.yaml` 词汇同源（key 一一对应）。每条五要素：说明 / 代表作 / 设计旋钮 / 常见坑 / 常见组合。设计新游戏时从这里取机制，从 `design/samples/` 取具体参考。

## 轻策/派对

### simultaneous-action · 同时行动 (Simultaneous Action Selection)
- 说明：所有玩家同时暗选行动再一起揭示，消除等待。
- 代表作：富饶之城、银河卡车司机、Roll for the Galaxy。
- 设计旋钮：行动选项数、揭示后的冲突结算规则、预测对手收益。
- 常见坑：选项间收益差太小→随便选都一样；冲突结算太惩罚→人人保守。
- 常见组合：+角色能力（ asymmetric 预测）、+骰子分配。

### reaction · 反应 (Reaction / Real-time)
- 说明：实时抢答/抢拍，拼反应速度而非深思。
- 代表作：德国心脏病、闪灵快手。
- 设计旋钮：触发条件复杂度、抢拍惩罚、回合节奏。
- 常见坑：纯拼手速→策略玩家流失；触发条件太隐蔽→长时间冷场。
- 常见组合：+模式识别、+吹牛（假触发）。

### word-guessing · 猜词 (Word Guessing)
- 说明：限制沟通下传递词语信息。
- 代表作：行动代号、只言片语、Codenames Duet。
- 设计旋钮：提示字数/形式限制、词库难度分布、团队 vs 合作。
- 常见坑：词库文化依赖太强；提示限制太松→退化成直接描述。
- 常见组合：+合作、+隐藏身份（内鬼干扰提示）。

### bluffing · 吹牛 (Bluffing)
- 说明：虚张声势与识破，信息不对称的心理博弈。
- 代表作：吹牛骰、政变、骷髅王。
- 设计旋钮：说谎收益/被抓惩罚比、信息可见度、出局机制。
- 常见坑：抓吹牛收益过高→没人敢吹→机制死掉；早期出局玩家干等。
- 常见组合：+隐藏身份、+押注博弈。

## 家庭/聚会

### set-collection · 成套收集 (Set Collection)
- 说明：收集特定组合换取递增收益。
- 代表作：璀璨宝石（贵族+发展卡）、寿司走！、拉密。
- 设计旋钮：成套曲线（线性/指数）、卡牌获取方式、干扰手段。
- 常见坑：指数曲线→先凑齐者滚雪球；无干扰→各玩各的 solitaire。
- 常见组合：+轮抽、+引擎构建、+交易。

### tile-placement · 板块放置 (Tile Placement)
- 说明：拼接板块构建空间结构并计分。
- 代表作：卡卡颂、银河卡车司机、Azul。
- 设计旋钮：板块形状/连接规则、计分触发时机、公共 vs 个人版图。
- 常见坑：空间规则学习成本高；后期放置无意义→垃圾时间。
- 常见组合：+模式构建、+区域控制。

### light-dice-strategy · 骰子轻策 (Light Dice Strategy)
- 说明：骰子结果提供约束，玩家在约束内做选择。
- 代表作：王权骰铸（轻量面）、骰子 forge、King of Tokyo。
- 设计旋钮：重掷次数/成本、骰面分配、保底机制。
- 常见坑：纯骰运无 mitigation→策略玩家不满；mitigation 太强→骰子形同虚设。
- 常见组合：+押注博弈、+可变玩家能力。

### trading-negotiation · 交易谈判 (Trading & Negotiation)
- 说明：玩家间自由交易资源/承诺。
- 代表作：卡坦岛、唐人街、Bohnanza。
- 设计旋钮：交易物品种类、 binding 承诺是否强制、稀缺度分布。
- 常见坑：资源互补性不足→无人交易；强者恒强→交易变成进贡。
- 常见组合：+成套收集、+网络构建。

## 策略核心

### worker-placement · 工人放置 (Worker Placement)
- 说明：把有限工人放到行动位执行行动，占位产生互动。
- 代表作： Agricola、Lords of Waterdeep、石器时代。
- 设计旋钮：工人数量/增长、占位排他性、行动位收益曲线、补偿机制。
- 常见坑：先手占位优势随人数放大→用回合轨/后手补偿；关键位唯一→被卡死体验差。
- 常见组合：+引擎构建、+资源管理、+进度轨。

### engine-building · 引擎构建 (Engine Building)
- 说明：前期投资构建产出系统，后期收益加速。
- 代表作：璀璨宝石、Gizmos、Wingspan。
- 设计旋钮：引擎组件成本/产出比、引擎上限、终局触发时机。
- 常见坑：引擎成型后无决策→autopilot；先成型者滚雪球→需要终局压缩或互动税。
- 常见组合：+成套收集、+卡牌驱动、+资源管理。

### area-control · 区域控制 (Area Control / Influence)
- 说明：在版图区域争夺存在感/控制权计分。
- 代表作：Risk、Small World、El Grande、Blood Rage。
- 设计旋钮：控制判定（多数/阈值）、战斗结算、区域价值差异、移动成本。
- 常见坑：强者被全员围殴→国王制造；滚雪球→需要落后补偿/不对称胜利条件。
- 常见组合：+卡牌驱动、+可变玩家能力、+拍卖。

### route-building · 路线构建 (Route / Network Building)
- 说明：建设连接节点的路径/网络，按连通性收益。
- 代表作：Ticket to Ride、铁路环游、Brass（网络面）。
- 设计旋钮：路径成本、连通计分公式、阻塞与共享规则。
- 常见坑：最优路线唯一→solitaire 竞速；网络效应过强→先发垄断。
- 常见组合：+拾取运送、+经济管理、+股份持有。

### action-point-allocation · 行动点分配 (Action Point Allocation)
- 说明：每回合固定行动点，自由分配到多种行动。
- 代表作：Flash Point、Pandemic（行动面）、Tikal。
- 设计旋钮：点数总量、行动成本表、存点/借贷规则。
- 常见坑：成本表失衡→唯一最优行动序列；点数太少→每回合被迫做同样的事。
- 常见组合：+合作、+网格移动、+区域控制。

## 卡牌

### hand-management · 手牌管理 (Hand Management)
- 说明：手牌是稀缺资源，何时出/留/弃是核心决策。
- 代表作：Brass（轻卡驱）、Concordia、Lost Cities。
- 设计旋钮：手牌上限、抽牌节奏、多用途张力、弃牌回收。
- 常见坑：手牌上限太紧→每回合被迫弃好牌挫败感；抽牌太顺→无张力。
- 常见组合：+多用途卡牌、+引擎构建、+路线构建。

### deck-building · 牌库构建 (Deck Building)
- 说明：游戏中购买卡牌强化个人牌库，循环抽用。
- 代表作：Dominion、Star Realms、Clank!。
- 设计旋钮：牌库循环速度、购买行供给、-trash 机制、终局计分卡比例。
- 常见坑：牌库稀释无 trash→后期抽不到关键牌；无限连 combo→需要上限或成本递增。
- 常见组合：+区域控制（Clank! 式）、+押注博弈。

### card-driven · 卡牌驱动 (Card Driven)
- 说明：卡牌决定可执行的行动/事件，驱动回合叙事。
- 代表作：Twilight Struggle、Here I Stand、Commands & Colors。
- 设计旋钮：事件/行动点双用途张力、卡牌历史事件密度、手牌信息可见度。
- 常见坑：事件卡强度失衡→必打/必弃；对手事件触发太惩罚→体验差。
- 常见组合：+区域控制、+手牌管理、+秘密目标。

### drafting · 轮抽 (Drafting)
- 说明：从公共池轮流挑选，选择本身传递信息。
- 代表作：7 Wonders、寿司走！、Between Two Cities。
- 设计旋钮：轮抽方向/轮数、信息可见度、pass 策略深度。
- 常见坑：首 pick 优势过大→蛇形/补偿；池子太小→后期无选择。
- 常见组合：+成套收集、+引擎构建、+同时行动。

### multi-use-cards · 多用途卡牌 (Multi-use Cards)
- 说明：一张牌多种用法，用掉即放弃其他用途。
- 代表作：Glory to Rome、Innovation、Race for the Galaxy。
- 设计旋钮：用途数量、用途间机会成本差、牌面信息密度。
- 常见坑：用途收益差太大→伪选择；牌面文本过载→学习成本爆炸。
- 常见组合：+手牌管理、+引擎构建、+行动点。

## 美式/叙事

### cooperation · 合作 (Cooperative Play)
- 说明：玩家共同对抗系统，共享胜负。
- 代表作：Pandemic、Spirit Island、Forbidden Island。
- 设计旋钮：系统压力曲线、信息限制、alpha player 防护、个人目标。
- 常见坑：老手指挥全场（alpha player）→用隐藏信息/实时/个人秘密目标缓解；难度只靠数值堆→无策略深度。
- 常见组合：+行动点、+可变玩家能力、+传承。

### legacy · 传承 (Legacy)
- 说明：跨局持久改变规则/组件，战役式体验。
- 代表作：Pandemic Legacy、Gloomhaven、Risk Legacy。
- 设计旋钮：持久变化粒度、解锁节奏、可重玩性、失败保护。
- 常见坑：早期决策锁死后期体验→需要 catch-up 或分支；组件破坏不可逆→玩家焦虑。
- 常见组合：+合作、+叙事、+可变玩家能力。

### narrative · 叙事 (Narrative / Storytelling)
- 说明：事件/文本驱动故事展开，选择有叙事后果。
- 代表作：T.I.M.E Stories、Detective、Above and Below。
- 设计旋钮：分支密度、文本量/局、叙事与机制耦合度、重玩价值。
- 常见坑：机制与故事两张皮；一次性谜题→二刷无价值。
- 常见组合：+合作、+传承、+逻辑推理。

### hidden-roles · 隐藏身份 (Hidden Roles)
- 说明：秘密阵营/身份，推理与欺骗并存。
- 代表作：阿瓦隆、狼人杀、Secret Hitler、One Night Ultimate Werewolf。
- 设计旋钮：阵营人数比、信息揭示节奏、出局机制、特殊能力。
- 常见坑：早期出局干等→用单夜制/无出局；阵营比失衡→某方必胜策略。
- 常见组合：+投票、+吹牛、+逻辑推理。

### combat-resolution · 战斗结算 (Combat Resolution)
- 说明：冲突的数值结算模型（骰/卡/确定性）。
- 代表作：Dice Throne、Risk、Small World、Blood Rage。
- 设计旋钮：随机度（骰面/卡驱/确定）、攻防不对称、损失持久性、mitigation。
- 常见坑：高随机+高损失→挫败；确定性过强→先手必胜可算尽。
- 常见组合：+可变玩家能力、+区域控制、+押注博弈。

## 抽象/双人

### abstract-chess · 抽象棋 (Abstract Strategy)
- 说明：完全信息、无随机（或极低）、纯决策对抗。
- 代表作：象棋、围棋、五子棋、Hive、Onitama。
- 设计旋钮：棋子能力不对称度、棋盘拓扑、先手补偿（贴目/交换规则）、和棋处理。
- 常见坑：先手优势无补偿；状态空间太小→可算尽；和棋过多→需要打破对称的计分。
- 常见组合：+模式构建、+网格移动。

### pattern-building · 模式构建 (Pattern Building)
- 说明：按目标图案/约束摆放元素。
- 代表作：Azul、Sagrada、Patchwork。
- 设计旋钮：图案约束硬度、公共目标 vs 私人目标、干扰手段。
- 常见坑：约束太硬→ puzzle 感无互动；太软→无成就感。
- 常见组合：+板块放置、+轮抽、+成套收集。

### logical-deduction · 逻辑推理 (Logical Deduction)
- 说明：从有限线索推断隐藏事实。
- 代表作：Cluedo、Cryptid、Search for Planet X、阿瓦隆（推理面）。
- 设计旋钮：线索信息量、询问成本、推理链长度、猜测惩罚。
- 常见坑：线索不足→纯猜；线索过足→首回合可解。
- 常见组合：+隐藏身份、+叙事、+合作。

## 硬核/重策

### heavy-composite · 资源管理+引擎+路线+推轨的复合体 (Heavy Composite)
- 说明：多子系统耦合的重策结构，不是单一机制。
- 代表作：Brass: Birmingham、Gaia Project、Terra Mystica。
- 设计旋钮：子系统数量与耦合强度、回合行动数、信息密度、终局计分维度。
- 常见坑：子系统各自为政→复合体名不副实；耦合过紧→一步错满盘输；教学成本 30 分钟+。
- 常见组合：本身即组合；拆解时按子系统分别对照 economic-management / engine-building / network-building / progress-track。

## 游戏级标签

### dice-driven · 骰子驱动 (Dice Driven)
- 说明：骰子结果是行动的主要输入/约束。
- 代表作：王权骰铸、King of Tokyo、Roll for the Galaxy。
- 设计旋钮：骰池大小、重掷/改面成本、骰面符号分布、保底。
- 常见坑：无 mitigation 的纯运；mitigation 成本失衡→骰子无意义。
- 常见组合：+可变玩家能力、+战斗结算、+押注博弈。

### card-assist · 卡牌辅助 (Card-assisted Abilities)
- 说明：卡牌作为能力的补充/升级层，不是主驱动。
- 代表作：王权骰铸（卡牌面）、Dice Forge。
- 设计旋钮：卡牌获取节奏、与主机制的耦合强度、手牌上限。
- 常见坑：辅助层喧宾夺主；卡牌强度方差过大。
- 常见组合：+骰子驱动、+可变玩家能力。

### voting · 投票 (Voting)
- 说明：集体表决决定结果，票权可不对称。
- 代表作：阿瓦隆（组队表决）、Secret Hitler、The Resistance。
- 设计旋钮：通过阈值、票权分配、否决成本、信息可见度。
- 常见坑：阈值失衡→某方恒过/恒否；投票无信息量→走过场。
- 常见组合：+隐藏身份、+谈判、+逻辑推理。

### network-building · 网络构建 (Network Building)
- 说明：建设互相连通的设施网络，连通性产生收益。
- 代表作：Brass: Birmingham、Power Grid、Railways of the World。
- 设计旋钮：连接成本、网络效应曲线、共享/阻塞规则、时代/阶段切换。
- 常见坑：网络效应过强→先发垄断；与路线构建区分：网络强调节点互联，路线强调路径延伸。
- 常见组合：+经济管理、+股份持有、+拾取运送。

### economic-management · 经济/资源管理 (Economic / Resource Management)
- 说明：资源的生产、转换、消费循环。
- 代表作：Brass、Power Grid、Le Havre、Agricola。
- 设计旋钮：资源种类数、转换链长度、市场定价（固定/动态）、稀缺度。
- 常见坑：资源死锁（无沉没口或无产出源）；动态市场可被操纵→需要规则护栏。
- 常见组合：+引擎构建、+网络构建、+工人放置。

## 设计扩展机制

### auction · 拍卖竞价 (Auction / Bidding)
- 说明：玩家出价竞争物品/行动权。
- 代表作：Modern Art、Ra、For Sale、QE。
- 设计旋钮：拍卖形式（公开/暗标/一口价/荷兰）、支付对象（银行/玩家）、流拍处理。
- 常见坑：估值信息不足→乱出价；赢家诅咒过强→无人敢拍。
- 常见组合：+成套收集、+经济管理、+股份持有。

### secret-objectives · 秘密目标 (Secret Objectives)
- 说明：私人目标提供不对称激励与隐藏信息。
- 代表作：Ticket to Ride（路线卡）、Twilight Imperium、Scythe（目标面）。
- 设计旋钮：目标难度分布、目标间冲突度、揭示时机、完成奖励。
- 常见坑：目标难度方差大→运气成分；目标与公共计分脱节→两张皮。
- 常见组合：+区域控制、+路线构建、+逻辑推理。

### progress-track · 进度轨 (Progress Track)
- 说明：沿轨道推进解锁能力/计分，常与其他机制耦合。
- 代表作：Gaia Project（科技轨）、Terra Mystica（ cult 轨）、Scythe（ popularity 轨）。
- 设计旋钮：推进成本曲线、轨道收益分布、多轨耦合、轨道上限。
- 常见坑：单轨最优→其他轨无人走；收益前置过强→早期冲轨唯一解。
- 常见组合：+引擎构建、+重策复合体、+可变玩家能力。

### dice-placement · 骰子放置 (Dice Placement)
- 说明：骰子作为工人放到行动位，面值约束行动强度。
- 代表作：The Voyages of Marco Polo、Dice Forge、Sagrada。
- 设计旋钮：面值-行动映射、改面成本、骰子回收节奏。
- 常见坑：低面值骰子无可用位→废骰挫败；改面太便宜→面值约束失效。
- 常见组合：+工人放置、+模式构建、+轻骰策。

### pick-up-and-deliver · 拾取运送 (Pick-up and Deliver)
- 说明：把货物从产地运到需求地赚差价。
- 代表作：Empire Builder、Railways of the World、Merchants & Marauders。
- 设计旋钮：供需分布、运输成本/容量、需求动态性、路线共享。
- 常见坑：最优解固定→背板；需求静态→后期无决策。
- 常见组合：+路线构建、+网络构建、+经济管理。

### stock-holding · 股份持有 (Stock Holding)
- 说明：持有公司/资产股份，分享其成长收益。
- 代表作：18xx 系列、Acquire、Chicago Express。
- 设计旋钮：股价模型、分红 vs 留存、控股门槛、破产/清算规则。
- 常见坑：控股者掏空公司→小股东体验差；股价可精确算尽→无博弈。
- 常见组合：+网络构建、+拍卖、+经济管理。

### time-track · 时间轨 (Time Track)
- 说明：行动消耗时间，时间轨位置决定回合顺序。
- 代表作：Thebes、Patchwork、Tokaido。
- 设计旋钮：时间成本表、落后补偿、轨道终点触发。
- 常见坑：慢行动收益不足→人人抢快；时间轨与回合顺序耦合过紧→长考惩罚过重。
- 常见组合：+板块放置、+成套收集、+行动点。

### variable-player-powers · 可变玩家能力 (Variable Player Powers)
- 说明：玩家起始能力不对称，重玩价值来源。
- 代表作：Cosmic Encounter、Spirit Island、王权骰铸（角色面）、Root。
- 设计旋钮：能力强度方差、能力间克制关系、能力成长线。
- 常见坑：能力强度失衡→选角即定胜负；能力过于独立→无互动。
- 常见组合：+区域控制、+合作、+战斗结算、+引擎构建。

### grid-movement · 网格移动 (Grid Movement)
- 说明：棋子在网格/图上按规则移动。
- 代表作：象棋类、Hive、Onitama、Survive: Escape from Atlantis。
- 设计旋钮：移动规则复杂度、地形效果、移动与行动的耦合。
- 常见坑：移动规则学习成本高；移动无代价→位置无意义。
- 常见组合：+抽象棋、+区域控制、+行动点。

### push-your-luck · 押注博弈 (Push Your Luck)
- 说明：继续冒险博更大收益，或见好就收。
- 代表作：Can't Stop、Deep Sea Adventure、Quacks of Quedlinburg。
- 设计旋钮：风险曲线（爆掉概率/损失）、收益曲线、收手时机信息。
- 常见坑：期望值唯一最优→无心理博弈；爆掉惩罚过重→人人保守。
- 常见组合：+骰子驱动、+袋构建（bag building）、+成套收集。