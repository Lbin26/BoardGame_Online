package org.dromara.magicalathlete.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.game.common.enums.RoomStatus;
import org.dromara.game.common.room.GameRoom;
import org.dromara.magicalathlete.domain.Racer;
import org.dromara.magicalathlete.domain.vo.MagicalAthleteRoomVO;
import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO;
import org.dromara.magicalathlete.domain.vo.MagicalAthleteSnapshotVO.ScoreVO;
import org.dromara.magicalathlete.engine.DraftSession;
import org.dromara.magicalathlete.engine.DraftSession.PickOutcome;
import org.dromara.magicalathlete.engine.RaceSession;
import org.dromara.magicalathlete.engine.RaceTrack;
import org.dromara.magicalathlete.engine.RaceVariant;
import org.dromara.magicalathlete.engine.ScoreBoard;
import org.dromara.magicalathlete.engine.ability.AbilityRegistry;
import org.dromara.magicalathlete.engine.ability.CopyDelegation;
import org.dromara.magicalathlete.engine.ability.FullAbilities;
import org.dromara.magicalathlete.engine.ability.TurnIntent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * 胡闹运动会房间。M2 覆盖：座位（2–6）、蛇形两批轮抽（D7）、30s 代选（D6）、
 * 赛前暗选（D5）、服务端掷骰（D4）、主要移动/冲线/奖牌结算与四场流程。
 * 能力系统（D3/M3）与循环保护（D8）留后续版本。
 *
 * <p>并发：所有状态变更入口 synchronized（轮抽、暗选、掷骰、超时托管共用一把锁）。
 */
@Slf4j
public class MagicalAthleteRoom extends GameRoom {

    /** M4：全量能力注册表（M3 12 + M4 12），无状态、线程安全，全房间共享一份 */
    private static final AbilityRegistry ABILITIES = FullAbilities.registry();

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;
    public static final int TOTAL_RACES = 4;

    /** 轮抽 30s；暗选 20s；回合 20s（D6） */
    public static final long DRAFT_TIMEOUT_MS = 30_000;
    public static final long MUSTER_TIMEOUT_MS = 20_000;
    public static final long TURN_TIMEOUT_MS = 20_000;

    public enum Phase {
        WAITING, DRAFT, MUSTER, RACE, RACE_END, FINISHED
    }

    public enum SeatResult { SUCCESS, NOT_WAITING, SEAT_TAKEN, ROOM_FULL, INVALID_SEAT }

    public enum StartResult { SUCCESS, NOT_HOST, NOT_WAITING, NOT_ENOUGH_PLAYERS }

    public enum PickResult { SUCCESS, NOT_IN_GAME, PHASE_MISMATCH, NOT_YOUR_TURN, RACER_NOT_IN_POOL, DRAFT_FINISHED }

    public enum ReadyResult {
        SUCCESS, NOT_IN_GAME, PHASE_MISMATCH, RACER_NOT_IN_HAND, ALREADY_READY, RACER_USED
    }

    public enum RollResult { SUCCESS, NOT_IN_GAME, PHASE_MISMATCH, NOT_YOUR_TURN, RACE_FINISHED }

    public enum OptionalResult {
        SUCCESS, NOT_IN_GAME, PHASE_MISMATCH, NOT_YOUR_TURN, INVALID_CHOICE, NO_OPTIONAL
    }

    /** 单场结算摘要（Handler 广播 RACE_RESULT 用） */
    public record Settlement(int raceNo, int goldSeat, int silverSeat, int goldValue, int silverValue,
                             boolean gameOver, List<RaceSession.Standing> standings) {
    }

    /** 掷骰结果：dice=null 表示动作被拒 */
    public record RollOutcome(boolean ok, RollResult reason, Integer seatNo, Integer dice,
                              List<String> effects, boolean tripSkipped, Settlement settlement,
                              List<String> abilityChain, boolean chainTruncated) {
        static RollOutcome fail(RollResult reason) {
            return new RollOutcome(false, reason, null, null, List.of(), false, null, List.of(), false);
        }

        /** 兼容 M2 老构造（无能力链） */
        public RollOutcome(boolean ok, RollResult reason, Integer seatNo, Integer dice,
                           List<String> effects, boolean tripSkipped, Settlement settlement) {
            this(ok, reason, seatNo, dice, effects, tripSkipped, settlement, List.of(), false);
        }
    }

    /** 超时代选（DRAFT_PICK 广播用） */
    public record AutoPick(int seatNo, String playerName, Racer racer) {
    }

    /** 超时暗选（MUSTER 广播用；仅广播"已准备"，key 不外泄） */
    public record AutoReady(int seatNo, String playerName, String racerKey) {
    }

    @Getter
    private final int maxPlayers;

    private final TreeMap<Integer, String> seats = new TreeMap<>();
    private final Map<Integer, Boolean> seatOnline = new HashMap<>();
    private final Map<String, Long> userIdByName = new LinkedHashMap<>();

    @Getter
    private volatile Phase phase = Phase.WAITING;

    /** M5：人数变体。默认普通局；3 人局可通过 START_GAME 显式开启 THREE_DOUBLE */
    @Getter
    private volatile RaceVariant variant = RaceVariant.NORMAL;

    /** 通用阶段截止时间（DRAFT/MUSTER/RACE 共用；保留旧 getter 名兼容 M1 测试） */
    @Getter
    private volatile long draftDeadlineAt;

    private volatile DraftSession draft;

    @Getter
    private volatile int raceNo;

    private volatile RaceSession race;
    private volatile ScoreBoard scoreboard;
    private final Set<String> usedRacers = new HashSet<>();
    /** 参赛号 lane → 本场出场选手 key；普通局 lane 恒等于真实座位 */
    private final Map<Integer, String> activeKeys = new LinkedHashMap<>();
    private final Set<Integer> readySeats = new LinkedHashSet<>();
    /** M5 双角色：真实座位 → 本场已暗选的选手 key（按提交顺序固定出场顺序） */
    private final Map<Integer, List<String>> musterKeys = new LinkedHashMap<>();
    /** M5：上一场冠军的出场选手 key（双胞胎赛前复制用），第一场为 null */
    private String lastWinnerKey = null;

    @Getter
    private volatile Integer lastDice;
    @Getter
    private volatile Integer diceSeatNo;
    @Getter
    private volatile List<String> lastEffects = List.of();

    /** M4：当前行动者声明的回合意图；ROLL 时取用并清空，超时/换手作废 */
    private volatile TurnIntent pendingIntent = TurnIntent.NONE;

    private Random random = new Random();

    public MagicalAthleteRoom(String roomId, String hostName, int maxPlayers) {
        super(roomId, hostName);
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("胡闹运动会房间容量必须 2-6: " + maxPlayers);
        }
        this.maxPlayers = maxPlayers;
    }

    // ---- 座位（与 halli 同构） ----

    public synchronized SeatResult takeSeat(String playerName, Integer seatNo) {
        if (getStatus() != RoomStatus.WAITING) return SeatResult.NOT_WAITING;
        boolean wasSeated = seats.containsValue(playerName);
        if (!wasSeated && seats.size() >= maxPlayers) return SeatResult.ROOM_FULL;
        Integer target = seatNo;
        if (target != null && (target < 1 || target > maxPlayers)) return SeatResult.INVALID_SEAT;
        if (target == null) target = firstFreeSeat();
        String occupied = seats.get(target);
        if (occupied != null && !occupied.equals(playerName)) return SeatResult.SEAT_TAKEN;

        Integer oldSeat = seatOf(playerName);
        if (oldSeat != null) {
            seats.remove(oldSeat);
            seatOnline.remove(oldSeat);
        }
        seats.put(target, playerName);
        seatOnline.put(target, isConnected(playerName));
        touch();
        log.info("房间 {} 入座: {} -> 座位 {}（{}/{}）", getRoomId(), playerName, target, seats.size(), maxPlayers);
        return SeatResult.SUCCESS;
    }

    public synchronized boolean standUp(String playerName) {
        if (getStatus() != RoomStatus.WAITING) return false;
        Integer seat = seatOf(playerName);
        if (seat == null) return false;
        seats.remove(seat);
        seatOnline.remove(seat);
        touch();
        return true;
    }

    // ---- 开局与轮抽 ----

    /** M1 原签名：默认变体（3 人局不会自动开启双角色） */
    public synchronized StartResult startGame(String playerName) {
        return startGame(playerName, null);
    }

    /**
     * M5：可显式指定变体。{@code variantRaw = "THREE_DOUBLE"} 且恰好 3 人时启用 3 人双角色局；
     * 其余取值一律按人数走默认变体，保证 M1–M4 行为零回归。
     */
    public synchronized StartResult startGame(String playerName, String variantRaw) {
        if (getHostName() == null || !getHostName().equals(playerName)) return StartResult.NOT_HOST;
        if (getStatus() != RoomStatus.WAITING) return StartResult.NOT_WAITING;
        if (seats.size() < MIN_PLAYERS) return StartResult.NOT_ENOUGH_PLAYERS;

        this.variant = RaceVariant.of(seats.size(), variantRaw);
        long seed = random.nextLong();
        initDraft(seed);
        log.info("房间 {} 开局轮抽: {} 人, 变体={}, seed={}", getRoomId(), seats.size(), variant, seed);
        return StartResult.SUCCESS;
    }

    private void initDraft(long seed) {
        this.draft = new DraftSession(seed, seats.size(), variant);
        this.phase = Phase.DRAFT;
        this.status = RoomStatus.PLAYING;
        this.raceNo = 0;
        this.race = null;
        this.scoreboard = null;
        this.usedRacers.clear();
        this.activeKeys.clear();
        this.readySeats.clear();
        this.musterKeys.clear();
        this.lastWinnerKey = null;
        this.lastDice = null;
        this.diceSeatNo = null;
        this.lastEffects = List.of();
        this.pendingIntent = TurnIntent.NONE;
        resetDeadline(DRAFT_TIMEOUT_MS);
        touch();
    }

    public synchronized PickResult pickRacer(String playerName, String racerKey) {
        Integer seat = seatOf(playerName);
        if (seat == null) return PickResult.NOT_IN_GAME;
        if (phase != Phase.DRAFT || draft == null) return PickResult.PHASE_MISMATCH;

        Integer expected = draft.currentSeatNo();
        if (expected == null || !expected.equals(seat)) return PickResult.NOT_YOUR_TURN;
        if (!draft.inPool(racerKey)) return PickResult.RACER_NOT_IN_POOL;

        PickOutcome outcome = draft.pick(seat, racerKey);
        if (!outcome.ok()) {
            return switch (outcome.reason()) {
                case "NOT_YOUR_TURN" -> PickResult.NOT_YOUR_TURN;
                case "DRAFT_FINISHED" -> PickResult.DRAFT_FINISHED;
                default -> PickResult.RACER_NOT_IN_POOL;
            };
        }
        afterPick();
        return PickResult.SUCCESS;
    }

    public synchronized Optional<AutoPick> autoPick(long now) {
        if (phase != Phase.DRAFT || draft == null || draft.finished()) return Optional.empty();
        if (now < draftDeadlineAt) return Optional.empty();
        Integer seat = draft.currentSeatNo();
        PickOutcome outcome = draft.autoPick();
        if (!outcome.ok()) return Optional.empty();
        afterPick();
        return Optional.of(new AutoPick(seat, seats.get(seat), outcome.racer()));
    }

    private void afterPick() {
        resetDeadline(DRAFT_TIMEOUT_MS);
        if (draft.finished()) enterMuster();
        touch();
    }

    /** 轮抽完成 → 赛前暗选（20s）；建立记分板，清空上一场临时状态 */
    private void enterMuster() {
        this.phase = Phase.MUSTER;
        this.race = null;
        this.activeKeys.clear();
        this.readySeats.clear();
        this.musterKeys.clear();
        this.lastDice = null;
        this.diceSeatNo = null;
        this.lastEffects = List.of();
        this.pendingIntent = TurnIntent.NONE;
        if (scoreboard == null) {
            List<RaceSession.Entrant> all = new ArrayList<>();
            draft.hands().forEach((seat, racers) -> {
                String name = seats.get(seat);
                if (name != null && !racers.isEmpty()) {
                    all.add(new RaceSession.Entrant(seat, name, racers.get(0).key()));
                }
            });
            scoreboard = new ScoreBoard(all);
        }
        resetDeadline(MUSTER_TIMEOUT_MS);
        log.info("房间 {} 第 {} 场赛前准备（{}s）", getRoomId(), raceNo + 1, MUSTER_TIMEOUT_MS / 1000);
    }

    // ---- 赛前暗选（D5） ----

    /**
     * 暗选出场选手：key 必须在自己手牌、未被过往场次使用、且本场本座位未重复提交。
     * M5 双角色局每座位需提交 {@code variant.racersPerSeat()} 名，提交顺序即赛前固定的出场顺序；
     * 未选满前不进入 readySeats。
     */
    public synchronized ReadyResult readyRacer(String playerName, String racerKey) {
        Integer seat = seatOf(playerName);
        if (seat == null) return ReadyResult.NOT_IN_GAME;
        if (phase != Phase.MUSTER) return ReadyResult.PHASE_MISMATCH;
        int need = variant.racersPerSeat();
        List<String> picked = musterKeys.computeIfAbsent(seat, k -> new ArrayList<>());
        if (picked.size() >= need || readySeats.contains(seat)) return ReadyResult.ALREADY_READY;
        if (draft == null || !handKeys(seat).contains(racerKey)) return ReadyResult.RACER_NOT_IN_HAND;
        if (usedRacers.contains(racerKey) || picked.contains(racerKey)) return ReadyResult.RACER_USED;

        picked.add(racerKey);
        if (picked.size() >= need) {
            bindMusterKeys(seat, picked);
            readySeats.add(seat);
        }
        if (readySeats.size() >= seats.size()) startRace();
        touch();
        return ReadyResult.SUCCESS;
    }

    /** M5：把一个座位已暗选的选手按固定顺序绑定到参赛号 lane */
    private void bindMusterKeys(int seat, List<String> keys) {
        for (int slot = 0; slot < keys.size(); slot++) {
            activeKeys.put(variant.laneOf(seat, slot), keys.get(slot));
        }
    }

    /** 超时暗选：为未准备的座位补齐手牌中未用过的选手（双角色补 2 名）；全员就绪则自动开局 */
    public synchronized List<AutoReady> autoReady(long now) {
        if (phase != Phase.MUSTER || now < draftDeadlineAt) return List.of();
        int need = variant.racersPerSeat();
        List<AutoReady> autos = new ArrayList<>();
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            int seat = e.getKey();
            if (readySeats.contains(seat)) continue;
            List<String> picked = musterKeys.computeIfAbsent(seat, k -> new ArrayList<>());
            for (String key : handKeys(seat)) {
                if (picked.size() >= need) break;
                if (usedRacers.contains(key) || picked.contains(key)) continue;
                picked.add(key);
                autos.add(new AutoReady(seat, e.getValue(), key));
            }
            if (picked.size() >= need) {
                bindMusterKeys(seat, picked);
                readySeats.add(seat);
            }
        }
        if (readySeats.size() >= seats.size()) startRace();
        touch();
        return autos;
    }

    /**
     * 全员就绪：建 RaceSession，进入 RACE（20s 回合超时）。
     * M5 双角色局里 {@code activeKeys} 的键是参赛号 lane，entrants 直接以 lane 为 seatNo，
     * 选手名按其归属的真实座位取；turnOrder 为 lane 升序，天然满足"回合内依次移动两名选手"。
     */
    private void startRace() {
        this.raceNo++;
        List<RaceSession.Entrant> entrants = new ArrayList<>(activeKeys.size());
        activeKeys.forEach((lane, key) -> {
            usedRacers.add(key);
            entrants.add(new RaceSession.Entrant(lane, seats.get(variant.seatOfLane(lane)), key));
        });
        entrants.sort((a, b) -> Integer.compare(a.seatNo(), b.seatNo()));
        List<RaceSession.CopyBinding> bindings = buildCopyBindings();
        this.race = new RaceSession(raceNo, entrants, ABILITIES, bindings);
        // M5：赛前能力（西西弗斯 +4、蛋蛋/双胞胎复制来的赛前能力）先结算
        List<String> preChain = this.race.applyPreRace();
        List<String> preEffects = new ArrayList<>();
        preChain.forEach(reason -> preEffects.add("ABILITY:" + reason));
        this.phase = Phase.RACE;
        this.readySeats.clear();
        this.lastDice = null;
        this.diceSeatNo = null;
        this.lastEffects = List.copyOf(preEffects);
        this.pendingIntent = TurnIntent.NONE;
        resetDeadline(TURN_TIMEOUT_MS);
        log.info("房间 {} 第 {}/{} 场比赛开始（{}）赛前能力链={}",
            getRoomId(), raceNo, TOTAL_RACES, race.side().label(), preChain);
    }

    /**
     * M5 赛前固定委托（D6 保守口径：不替玩家做"可以"类主观选择，用确定性规则）：
     * <ul>
     *   <li>双胞胎：复制上一场冠军的出场 key（第一场无委托）；</li>
     *   <li>蛋蛋：从自己未出场的手牌里取第一张"已实现能力且非 copy 类"的 key 复制；候选不回牌堆
     *       （这些卡本就留在手牌，不参与本场，天然不回池）；无合格候选则无委托。</li>
     * </ul>
     */
    private List<RaceSession.CopyBinding> buildCopyBindings() {
        List<RaceSession.CopyBinding> out = new ArrayList<>();
        // M5：键是参赛号 lane；CopyBinding.copySeat 也用 lane，手牌按归属真实座位取
        activeKeys.forEach((lane, key) -> {
            int seat = variant.seatOfLane(lane);
            if (CopyDelegation.TWIN.equals(key) && lastWinnerKey != null && ABILITIES.supports(lastWinnerKey)) {
                out.add(new RaceSession.CopyBinding(lane, lastWinnerKey, -1));
                log.info("房间 {} 座位 {} 双胞胎复制上场冠军 {}", getRoomId(), seat, lastWinnerKey);
            } else if (CopyDelegation.EGG.equals(key)) {
                String src = handKeys(seat).stream()
                    .filter(k -> !k.equals(key) && !usedRacers.contains(k) && ABILITIES.supports(k)
                        && !CopyDelegation.isCopyKey(k))
                    .findFirst().orElse(null);
                if (src != null) {
                    out.add(new RaceSession.CopyBinding(lane, src, -1));
                    log.info("房间 {} 座位 {} 蛋蛋复制 {}", getRoomId(), seat, src);
                }
            }
        });
        return out;
    }

    // ---- 可选能力意图（M4） ----

    /**
     * 声明本回合可选能力意图（USE_OPTIONAL）。服务端权威：座位由玩家名反查，
     * 意图值按当前出场选手可选项与合法范围校验；必须在自己回合、RACE 阶段声明。
     */
    public synchronized OptionalResult useOptional(String playerName, TurnIntent rawIntent) {
        Integer seat = seatOf(playerName);
        if (seat == null) return OptionalResult.NOT_IN_GAME;
        if (phase != Phase.RACE || race == null) return OptionalResult.PHASE_MISMATCH;
        Integer current = race.currentSeatNo();
        // M5：双角色局 current 是 lane，需按归属真实座位判断"轮到我"
        if (current == null || variant.seatOfLane(current) != seat) return OptionalResult.NOT_YOUR_TURN;
        final TurnIntent intent = rawIntent == null ? TurnIntent.NONE : rawIntent;

        List<String> options = optionalOptionsFor(current);
        boolean wants = intent.usePrimary() || intent.hasTargetSeat() || intent.hasTargetSpace() || intent.hasGuess();
        boolean hasOption = intent.usePrimary() && options.contains("USE_PRIMARY")
            || intent.hasTargetSeat() && options.contains("TARGET_SEAT")
            || intent.hasTargetSpace() && options.contains("TARGET_SPACE")
            || intent.hasGuess() && options.contains("GUESS_DICE");
        if (wants && !hasOption) return OptionalResult.NO_OPTIONAL;
        // 目标必须合法（在场、未淘汰、未冲线；目标格 1..29）
        if (intent.hasTargetSeat()) {
            boolean valid = race.racers().stream().anyMatch(s ->
                s.seatNo() == intent.targetSeat() && s.finishRank() == 0);
            if (!valid) return OptionalResult.INVALID_CHOICE;
        }
        if (intent.hasTargetSpace() && (intent.targetSpace() < 1 || intent.targetSpace() > 29)) {
            return OptionalResult.INVALID_CHOICE;
        }
        if (intent.hasGuess() && (intent.guessDice() < 1 || intent.guessDice() > 6)) {
            return OptionalResult.INVALID_CHOICE;
        }
        this.pendingIntent = intent;
        touch();
        return OptionalResult.SUCCESS;
    }

    /**
     * 当前座位可用的可选能力项（下发到快照，供前端渲染按钮）。
     * 参数是<b>真实座位</b>；M5 双角色局内部换算成该座位当前行动的 lane 后查询，
     * 普通局 lane==seat，行为零回归。
     */
    public synchronized List<String> optionalOptions(int seat) {
        Integer lane = currentLaneOfSeat(seat);
        return optionalOptionsFor(lane == null ? variant.laneOf(seat, 0) : lane);
    }

    private List<String> optionalOptionsFor(int seat) {
        String key = activeKeys.get(seat);
        if (key == null) return List.of();
        return switch (key) {
            case "legs", "rocket_scientist", "cheerleader" -> List.of("USE_PRIMARY");
            case "flip_flop", "hypnotist", "suckerfish" -> List.of("TARGET_SEAT");
            case "third_wheel" -> List.of("TARGET_SPACE");
            // M5 幕后军师：targetSeat 表示"预测谁夺冠"（首回合锁定）
            case "mastermind" -> List.of("TARGET_SEAT");
            case "genius" -> List.of("GUESS_DICE");
            case "magician", "dicemonger" -> List.of("USE_PRIMARY");
            default -> List.of();
        };
    }

    // ---- 掷骰与结算 ----

    /** 服务端掷骰：客户端只发 ROLL，点数不可由客户端指定（D4） */
    public synchronized RollOutcome roll(String playerName) {
        Integer seat = seatOf(playerName);
        if (seat == null) return RollOutcome.fail(RollResult.NOT_IN_GAME);
        Integer lane = currentLaneOfSeat(seat);
        if (lane == null) return RollOutcome.fail(RollResult.NOT_YOUR_TURN);
        return doRoll(lane, false);
    }

    /** 超时代掷：500ms tick 调用，为当前行动 lane 掷骰 */
    public synchronized RollOutcome autoRoll(long now) {
        if (phase != Phase.RACE || race == null || now < draftDeadlineAt) return RollOutcome.fail(RollResult.PHASE_MISMATCH);
        Integer lane = race.currentSeatNo();
        return lane == null ? RollOutcome.fail(RollResult.RACE_FINISHED) : doRoll(lane, true);
    }

    /** M5：参数 lane 为内部参赛号；对外 seatNo 一律换算回真实座位 */
    private RollOutcome doRoll(int lane, boolean auto) {
        if (phase != Phase.RACE || race == null) return RollOutcome.fail(RollResult.PHASE_MISMATCH);
        Integer current = race.currentSeatNo();
        if (current == null || !current.equals(lane)) return RollOutcome.fail(RollResult.NOT_YOUR_TURN);

        // M4：取出本回合意图（超时托管 auto=true 时放弃全部可选能力）
        TurnIntent intent = auto ? TurnIntent.NONE : this.pendingIntent;
        this.pendingIntent = TurnIntent.NONE;

        // 服务端掷骰（D4）：重掷类在服务端完成，弃用点数不进入能力链。
        // 魔术师最多重掷两次（共 3 次取最后）、骰子商人一次（共 2 次取最后），需玩家声明启用。
        int dice = 1 + random.nextInt(6);
        String key = activeKeys.get(lane);
        int rerolls = 0;
        if (!auto && intent.usePrimary()) {
            if ("magician".equals(key)) rerolls = 2;
            else if ("dicemonger".equals(key)) rerolls = 1;
        }
        for (int i = 0; i < rerolls; i++) dice = 1 + random.nextInt(6);

        RaceSession.RollOutcome out = race.roll(lane, dice, intent);
        if (!out.ok()) {
            return switch (out.reason()) {
                case RACE_FINISHED -> RollOutcome.fail(RollResult.RACE_FINISHED);
                default -> RollOutcome.fail(RollResult.NOT_YOUR_TURN);
            };
        }
        int ownerSeat = variant.seatOfLane(lane);
        this.lastDice = dice;
        this.diceSeatNo = ownerSeat;
        this.lastEffects = out.effects();

        Settlement settlement = null;
        if (out.raceFinished()) {
            settlement = settleRace();
        } else {
            resetDeadline(TURN_TIMEOUT_MS);
        }
        touch();
        return new RollOutcome(true, RollResult.SUCCESS, ownerSeat, dice, out.effects(),
            out.reason() == RaceSession.RollReason.TRIPPED_SKIP, settlement,
            out.abilityChain(), out.chainTruncated());
    }

    /** 第二名冲线 → 按本场奖牌面值记分 → 进入下一场暗选或终局 */
    private Settlement settleRace() {
        // M5：引擎 standings 的 seatNo 是参赛号 lane；记分/对外一律换算回真实座位，
        // 铜币与能力分按 lane→seat 归并（同座两名选手的数值相加）。
        List<RaceSession.Standing> raw = race.standings();
        int goldLane = raw.stream().filter(s -> s.finishRank() == 1).findFirst()
            .map(RaceSession.Standing::seatNo).orElse(0);
        int silverLane = raw.stream().filter(s -> s.finishRank() == 2).findFirst()
            .map(RaceSession.Standing::seatNo).orElse(0);
        int goldSeat = goldLane > 0 ? variant.seatOfLane(goldLane) : 0;
        int silverSeat = silverLane > 0 ? variant.seatOfLane(silverLane) : 0;
        // M5 幕后军师自猜命中：冠军同时获银牌（金银同人）
        boolean selfWin = race.isMastermindSelfWin();
        if (selfWin && goldSeat > 0) silverSeat = goldSeat;
        Map<Integer, Integer> bronzeBySeat = mergeLanes(race.bronzeBySeat());
        ScoreBoard.RaceAward award = selfWin
            ? scoreboard.awardRaceSelfGoldSilver(raceNo, goldSeat, bronzeBySeat)
            : scoreboard.awardRace(raceNo, goldSeat, silverSeat, bronzeBySeat);
        scoreboard.awardAbilities(raceNo, mergeLanes(race.extraScoreBySeat()));
        // M5：记录本场冠军出场 key，供下一场双胞胎复制（activeKeys 键为 lane）
        if (goldLane > 0) this.lastWinnerKey = activeKeys.get(goldLane);
        phase = Phase.RACE_END;
        log.info("房间 {} 第 {} 场结束：金 {} / 银 {}", getRoomId(), raceNo, goldSeat, silverSeat);

        // 对外 standings 用真实座位（前端按 mySeat 高亮），保留 racerKey 区分同座两名选手
        List<RaceSession.Standing> standings = raw.stream()
            .map(s -> new RaceSession.Standing(variant.seatOfLane(s.seatNo()), s.name(),
                s.racerKey(), s.position(), s.finishRank(), s.bronze()))
            .toList();
        boolean gameOver = raceNo >= TOTAL_RACES;
        Settlement result = new Settlement(raceNo, goldSeat, silverSeat,
            award.goldValue(), award.silverValue(), gameOver, standings);
        if (gameOver) {
            phase = Phase.FINISHED;
            status = RoomStatus.FINISHED;
            draftDeadlineAt = 0;
            log.info("房间 {} 四场结束，终局排名: {}", getRoomId(), scoreboard.rankings());
        } else {
            enterMuster();
        }
        return result;
    }

    private void resetDeadline(long timeoutMs) {
        this.draftDeadlineAt = System.currentTimeMillis() + timeoutMs;
    }

    /** M5：当前行动 lane 若归属于该真实座位则返回它，否则 null。普通局 lane==seat，等价原比较 */
    private Integer currentLaneOfSeat(int seat) {
        if (race == null) return null;
        Integer lane = race.currentSeatNo();
        return lane != null && variant.seatOfLane(lane) == seat ? lane : null;
    }

    /** M5：把以 lane 为键的映射归并到真实座位（同座两名选手的数值相加） */
    private Map<Integer, Integer> mergeLanes(Map<Integer, Integer> byLane) {
        if (byLane == null || byLane.isEmpty()) return Map.of();
        Map<Integer, Integer> bySeat = new LinkedHashMap<>();
        byLane.forEach((lane, value) ->
            bySeat.merge(variant.seatOfLane(lane), value, Integer::sum));
        return bySeat;
    }

    private List<String> handKeys(int seat) {
        if (draft == null) return List.of();
        return draft.hands().getOrDefault(seat, List.of()).stream().map(Racer::key).toList();
    }

    /** 参赛号 → 归属真实座位（双角色快照用；普通局恒等映射） */
    public synchronized Map<Integer, Integer> laneToSeat() {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        if (race != null) {
            for (RaceSession.RacerState r : race.racers()) {
                map.put(r.seatNo(), variant.seatOfLane(r.seatNo()));
            }
        }
        return map;
    }

    // ---- 查询与视图 ----

    public MagicalAthleteRoomVO toVO(String viewerName) {
        List<MagicalAthleteRoomVO.SeatVO> seatVOs = new ArrayList<>(maxPlayers);
        for (int no = 1; no <= maxPlayers; no++) {
            String name = seats.get(no);
            seatVOs.add(new MagicalAthleteRoomVO.SeatVO(no, name,
                name != null && isConnected(name), name != null && isAfk(name)));
        }
        return new MagicalAthleteRoomVO(getRoomId(), getHostName(), maxPlayers, getStatus(), seatVOs,
            seatOf(viewerName), afkPlayers());
    }

    /** 阶段快照：MUSTER 按收件人隐藏他人暗选；RACE+ 全量 */
    public MagicalAthleteSnapshotVO snapshot() {
        return snapshot(null);
    }

    public synchronized MagicalAthleteSnapshotVO snapshot(String viewerName) {
        List<MagicalAthleteSnapshotVO.PlayerVO> players = new ArrayList<>(seats.size());
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            String name = e.getValue();
            players.add(new MagicalAthleteSnapshotVO.PlayerVO(e.getKey(), name,
                isConnected(name), isAfk(name)));
        }

        if (phase == Phase.WAITING || draft == null) {
            return emptySnapshot(players);
        }

        boolean draftPhase = phase == Phase.DRAFT;
        boolean musterPhase = phase == Phase.MUSTER;
        boolean raceVisible = phase == Phase.RACE || phase == Phase.RACE_END || phase == Phase.FINISHED;

        Map<Integer, List<String>> hands = new LinkedHashMap<>();
        if (draftPhase || musterPhase) {
            Integer viewerSeat = viewerName == null ? null : seatOf(viewerName);
            draft.hands().forEach((seat, racers) -> {
                // MUSTER 只下发自己的手牌，避免顺带泄露未选的出场候选
                if (musterPhase && !seat.equals(viewerSeat)) return;
                hands.put(seat, racers.stream().map(Racer::key).toList());
            });
        }

        List<String> poolKeys = new ArrayList<>();
        if (draftPhase) for (Racer r : draft.pool()) poolKeys.add(r.key());

        List<Integer> ready = musterPhase ? new ArrayList<>(readySeats) : List.of();
        // M5：对外 activeRacerKeys 保持"真实座位 → 该座位第一名选手"的兼容语义；
        // 双角色局的全部选手另用 activeRacerKeysByLane（lane → key）精确下发。
        Map<String, String> activePublic = new LinkedHashMap<>();
        Map<String, String> activeByLane = new LinkedHashMap<>();
        Map<Integer, Integer> laneToSeatMap = new LinkedHashMap<>();
        if (raceVisible) {
            activeKeys.forEach((lane, key) -> {
                int seat = variant.seatOfLane(lane);
                activePublic.putIfAbsent(String.valueOf(seat), key);
                activeByLane.put(String.valueOf(lane), key);
                laneToSeatMap.put(lane, seat);
            });
        }
        // MUSTER 个性化：从已暗选列表取（未选满时 activeKeys 尚未绑定）
        Integer viewerSeatNo = viewerName == null ? null : seatOf(viewerName);
        List<String> myKeys = musterPhase && viewerSeatNo != null
            ? List.copyOf(musterKeys.getOrDefault(viewerSeatNo, List.of())) : List.of();
        String myKey = myKeys.isEmpty() ? null : myKeys.get(0);
        // MUSTER 公开进度：真实座位 → 已暗选数（不含 key，不泄露具体选手）
        Map<Integer, Integer> musterProgress = new LinkedHashMap<>();
        if (musterPhase) {
            for (int seat : seats.keySet()) {
                musterProgress.put(seat, musterKeys.getOrDefault(seat, List.of()).size());
            }
        }

        List<MagicalAthleteSnapshotVO.RacerStateVO> racerVOs = List.of();
        if (race != null && raceVisible) {
            racerVOs = race.racers().stream()
                .map(r -> new MagicalAthleteSnapshotVO.RacerStateVO(r.seatNo(), r.name(), r.racerKey(),
                    r.position(), r.tripped(), r.finishRank(), r.bronze(),
                    variant.seatOfLane(r.seatNo())))
                .toList();
        }

        Map<Integer, Integer> scores = new LinkedHashMap<>();
        List<ScoreVO> rankings = List.of();
        if (scoreboard != null) {
            scoreboard.rankings().forEach(s -> scores.put(s.seatNo(), s.total()));
            if (phase == Phase.FINISHED) {
                rankings = scoreboard.rankings().stream()
                    .map(s -> new ScoreVO(s.seatNo(), s.name(), s.gold(), s.silver(), s.bronze(),
                        s.total(), s.rank()))
                    .toList();
            }
        }

        Integer goldSeat = null;
        Integer silverSeat = null;
        if (raceVisible && scoreboard != null && !scoreboard.awards().isEmpty()) {
            ScoreBoard.RaceAward last = scoreboard.awards().get(scoreboard.awards().size() - 1);
            if (last.raceNo() == raceNo) {
                goldSeat = last.goldSeat();
                silverSeat = last.silverSeat();
            }
        }

        // M4：仅当收件人正在 RACE 且轮到自己时下发可选能力项（个性化，不泄露他人选择）。
        // M5 双角色：current 为 lane，按归属真实座位判断；选项仍按 lane 取 activeKeys。
        Integer currentLane = raceVisible && race != null ? race.currentSeatNo() : null;
        Integer currentOwnerSeat = currentLane == null ? null : variant.seatOfLane(currentLane);
        List<String> myOptions = List.of();
        if (phase == Phase.RACE && race != null && viewerName != null
            && currentOwnerSeat != null && currentOwnerSeat.equals(viewerSeatNo)) {
            myOptions = optionalOptionsFor(currentLane);
        }

        // M5：对外 currentSeatNo 恒为真实座位，保证前端"轮到我"与名字播报零改动
        Integer publicCurrentSeat = draftPhase ? draft.currentSeatNo() : currentOwnerSeat;
        return new MagicalAthleteSnapshotVO(
            phase.name(), raceNo, draftPhase ? draft.batch() : 0,
            draftPhase ? draft.pickOrder() : List.of(),
            draftPhase ? draft.pickIndex() : 0,
            poolKeys, hands,
            publicCurrentSeat,
            draftDeadlineAt, draft.finished(), players,
            ready, activePublic, myKey, racerVOs,
            lastDice, diceSeatNo, lastEffects, goldSeat, silverSeat, scores, rankings, myOptions,
            variant.name(), variant.racersPerSeat(), currentLane,
            laneToSeatMap, activeByLane, myKeys, musterProgress);
    }

    private MagicalAthleteSnapshotVO emptySnapshot(List<MagicalAthleteSnapshotVO.PlayerVO> players) {
        return new MagicalAthleteSnapshotVO(phase.name(), raceNo, 0, List.of(), 0,
            List.of(), Map.of(), null, 0, false, players,
            List.of(), Map.of(), null, List.of(), null, null, List.of(),
            null, null, Map.of(), List.of(), List.of(),
            variant.name(), variant.racersPerSeat(), null,
            Map.of(), Map.of(), List.of(), Map.of());
    }

    public String nameOfSeat(int seatNo) {
        return seats.get(seatNo);
    }

    public synchronized void bindUser(String playerName, Long userId) {
        if (playerName != null && userId != null && userId > 0) {
            userIdByName.put(playerName, userId);
        }
    }

    public long boundUserIdOf(String playerName) {
        Long bound = userIdByName.get(playerName);
        if (bound != null) return bound;
        Integer seat = seatOf(playerName);
        return seat == null ? 0L : -(long) seat;
    }

    @Override
    protected void onConnectedChanged(String playerName, boolean connected) {
        Integer seat = seatOf(playerName);
        if (seat != null) seatOnline.put(seat, connected);
    }

    @Override
    protected Collection<String> participantNames() {
        return new ArrayList<>(seats.values());
    }

    public Integer seatOf(String playerName) {
        if (playerName == null) return null;
        for (Map.Entry<Integer, String> e : seats.entrySet()) {
            if (e.getValue().equals(playerName)) return e.getKey();
        }
        return null;
    }

    public int seatedCount() {
        return seats.size();
    }

    public List<String> seatOrder() {
        return new ArrayList<>(seats.values());
    }

    private Integer firstFreeSeat() {
        for (int no = 1; no <= maxPlayers; no++) {
            if (!seats.containsKey(no)) return no;
        }
        return null;
    }

    /** 供测试注入种子：确定性轮抽 + 确定性骰点 */
    public synchronized void startGameWithSeed(String playerName, long seed) {
        startGameWithSeed(playerName, seed, null);
    }

    /** M5：供测试注入种子并显式指定变体（3 人双角色集成测试用） */
    public synchronized void startGameWithSeed(String playerName, long seed, String variantRaw) {
        if (getHostName() == null || !getHostName().equals(playerName)) return;
        if (getStatus() != RoomStatus.WAITING || seats.size() < MIN_PLAYERS) return;
        this.random = new Random(seed);
        this.variant = RaceVariant.of(seats.size(), variantRaw);
        initDraft(seed);
    }
}
