package org.dromara.magicalathlete.engine.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能力事件注册表（D3：racerKey + eventType → Handler 列表）。
 *
 * <p>不是 switch：新增选手只需 register 一条，不改引擎代码。一个事件可挂多个 handler
 * （例如西西弗斯在 BEFORE_RACE 与 DICE_RESULT 各一条），返回顺序即执行顺序。
 */
public final class AbilityRegistry {

    private final Map<String, List<AbilityHandler>> handlers = new HashMap<>();
    /** M5：通过委托实现能力的选手（copy 类），无独立 handler，但可安全开放进牌池 */
    private final Set<String> delegatedKeys = new HashSet<>();

    /** 注册：同一 key+event 可多次注册，按注册顺序执行 */
    public AbilityRegistry register(String racerKey, AbilityEvent event, AbilityHandler handler) {
        if (racerKey == null || event == null || handler == null) {
            throw new IllegalArgumentException("注册三要素不可为空");
        }
        handlers.computeIfAbsent(cacheKey(racerKey, event), k -> new ArrayList<>()).add(handler);
        return this;
    }

    public List<AbilityHandler> get(String racerKey, AbilityEvent event) {
        return handlers.getOrDefault(cacheKey(racerKey, event), List.of());
    }

    public boolean has(String racerKey, AbilityEvent event) {
        return !get(racerKey, event).isEmpty();
    }

    /** 已注册能力覆盖的选手数量（按 racerKey 去重），供 D10 牌池过滤使用 */
    public int coveredRacerCount() {
        return (int) handlers.keySet().stream().map(k -> k.split("#", 2)[0]).distinct().count();
    }

    public List<String> coveredRacerKeys() {
        return handlers.keySet().stream().map(k -> k.split("#", 2)[0]).distinct().sorted().toList();
    }

    /** 登记一个"通过委托实现能力"的选手（M5 copy 类）：无 handler 但允许进牌池 */
    public AbilityRegistry registerDelegated(String racerKey) {
        if (racerKey != null) delegatedKeys.add(racerKey);
        return this;
    }

    /** 登记一个"由引擎直接实现、无需事件 handler"的选手（M5 跳跳蟾蜍特殊移动） */
    public AbilityRegistry registerEngineManaged(String racerKey) {
        return registerDelegated(racerKey);
    }

    /** 选手是否具备任意已实现能力（D10：牌池只开放已实现能力的选手；含委托实现） */
    public boolean supports(String racerKey) {
        return delegatedKeys.contains(racerKey)
            || handlers.keySet().stream().anyMatch(k -> k.startsWith(racerKey + "#"));
    }

    private static String cacheKey(String racerKey, AbilityEvent event) {
        return racerKey + "#" + event.name();
    }
}
