package org.dromara.magicalathlete.engine;

import org.dromara.magicalathlete.domain.Racer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 36 名选手目录（D2：单一事实来源是 game.yaml，这里逐条镜像为代码常量）。
 *
 * <p>构造器内断言 36 条且 key 唯一，避免数据集与实现漂移。能力摘要与
 * {@code design/games/magicalAthlete/rules.md} 表一致；M1 只用 key/name/type 做展示与牌池，
 * 能力结算（D3）在 M3 用事件注册表接入。
 */
public final class RacerCatalog {

    /** 声明顺序与 game.yaml.decks[0].cards 完全一致 */
    private static final List<Racer> ALL = List.of(
        r("alchemist", "炼金术士", "fixed-move", "主骰1/2可把移动基数替换为4，再加修正"),
        r("baba_yaga", "鸡脚屋", "interrupt", "单独停入或被停入时令对方绊倒"),
        r("banana", "香蕉", "interrupt", "被一次移动越过时令移动者绊倒，含反向越过"),
        r("blimp", "飞艇", "mod-move", "回合起点在第二角前主移动+3，否则−1"),
        r("centaur", "半人马", "interrupt", "超越时对方后退2格，后退越过也触发"),
        r("cheerleader", "啦啦队长", "mod-move", "可令所有最后名同时+2，然后自己+1"),
        r("coach", "教练", "mod-move", "所在格全部运动员主移动+1"),
        r("copycat", "模仿猫", "copy", "持续复制当前领先者能力，不复制赛前能力"),
        r("dicemonger", "骰子商人", "reactive", "每人每回合可额外重掷一次；别人重掷时自己+1"),
        r("duelist", "决斗家", "interrupt", "同格可决斗，比骰高者+2"),
        r("egg", "蛋蛋", "copy", "赛前抽三张选一复制其能力"),
        r("flip_flop", "人字拖", "warp", "可用与另一选手交换位置替代主移动"),
        r("genius", "天才", "reactive", "猜中最终主骰点数获得额外回合"),
        r("gunk", "黏液怪", "mod-move", "其他选手主移动−1，多个叠加"),
        r("hare", "野兔", "mod-move", "主移动+2；独自领先则跳过主移动；不发铜分"),
        r("heckler", "起哄者", "reactive", "任一选手回合结束位移≤1时自己+2"),
        r("huge_baby", "巨婴", "interrupt", "非起点禁止共格，把后来者放到其后一格"),
        r("hypnotist", "催眠师", "warp", "回合初可传送一名选手到自己所在格"),
        r("inchworm", "尺蠖", "interrupt", "别人最终主骰为1时取消其主移动，自己+1"),
        r("lackey", "跟班", "reactive", "别人最终主骰为6时自己先+2"),
        r("leaptoad", "跳跳蟾蜍", "interrupt", "移动只计空格，每跳过一个占据格算一次发动"),
        r("legs", "大长腿", "fixed-move", "可不掷骰，主移动改为5，仍受修正"),
        r("lovable_loser", "可爱的倒霉蛋", "scoring", "回合初独自垫底获得1分"),
        r("magician", "魔术师", "reactive", "主骰最多重掷两次，使用最终结果"),
        r("mastermind", "幕后军师", "scoring", "首回合初预测冠军，猜中立即获第二并结束比赛"),
        r("mouth", "大嘴怪", "interrupt", "停入恰有一名对手的格时淘汰该对手"),
        r("party_animal", "派对动物", "mod-move", "回合初其他人同时向自己移动1；同格每人使主移动+1"),
        r("rocket_scientist", "火箭科学家", "mod-move", "主骰可翻倍再加修正，完成后自己绊倒"),
        r("romantic", "浪漫家", "reactive", "有人停入恰有一人的格时自己+2"),
        r("scoocher", "蹭蹭怪", "reactive", "别人能力每次实际发动自己+1，不限每回合一次"),
        r("sisyphus", "西西弗斯", "scoring", "赛前+4分；主骰为6传送回起点并−1分"),
        r("skipper", "船长", "interrupt", "任一最终主骰为1时下一个行动者改为自己"),
        r("stickler", "较真怪", "interrupt", "别人所有移动必须恰好冲线，过量则整次取消"),
        r("suckerfish", "吸盘鱼", "warp", "开始移动前选择跟随，同组同时到达"),
        r("third_wheel", "电灯泡", "warp", "回合初可传送至恰有两人的格，之后仍有主移动"),
        r("twin", "双胞胎", "copy", "赛前可复制上一场冠军能力")
    );

    private static final Map<String, Racer> BY_KEY;

    static {
        Map<String, Racer> map = new LinkedHashMap<>();
        for (Racer r : ALL) {
            if (map.putIfAbsent(r.key(), r) != null) {
                throw new IllegalStateException("选手 key 重复: " + r.key());
            }
        }
        if (ALL.size() != 36) {
            throw new IllegalStateException("选手数量必须为 36，实际: " + ALL.size());
        }
        BY_KEY = Collections.unmodifiableMap(map);
    }

    private RacerCatalog() {
    }

    private static Racer r(String key, String name, String type, String summary) {
        return new Racer(key, name, type, summary);
    }

    /** 全量 36 名（不可变） */
    public static List<Racer> all() {
        return ALL;
    }

    public static Racer of(String key) {
        return BY_KEY.get(key);
    }

    public static boolean exists(String key) {
        return BY_KEY.containsKey(key);
    }

    /** 按 seed 洗牌生成一局牌堆（36 张，每张唯一） */
    public static List<Racer> shuffled(long seed) {
        List<Racer> deck = new ArrayList<>(ALL);
        Collections.shuffle(deck, new java.util.Random(seed));
        return deck;
    }

    /** 中文字段名（能力展示兜底） */
    public static String nameOf(String key) {
        Racer r = BY_KEY.get(key);
        return r == null ? key : r.name();
    }
}
