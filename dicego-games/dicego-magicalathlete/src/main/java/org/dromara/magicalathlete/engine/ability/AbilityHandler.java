package org.dromara.magicalathlete.engine.ability;

import java.util.List;

/**
 * 单个能力处理器：只读取上下文，产出效果指令列表，绝不直接改房间状态（D3）。
 *
 * <p>注册键为 {@code racerKey + eventType}；同一选手在不同事件可注册多个 handler。
 * handler 必须保持无副作用与确定性（同样的 ctx 快照产出同样的 effects），
 * 这是效果可重放与循环检测成立的前提。
 */
@FunctionalInterface
public interface AbilityHandler {

    List<Effect> apply(AbilityContext ctx);

    /** 空能力：未注册 handler 的事件用不到，注册时校验非空 */
    static AbilityHandler noop() {
        return ctx -> List.of();
    }
}
