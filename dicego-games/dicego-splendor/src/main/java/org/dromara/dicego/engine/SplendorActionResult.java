package org.dromara.dicego.engine;

import org.dromara.dicego.enums.SplendorFailReason;

import java.util.Map;

/**
 * 行动结算结果（引擎唯一出口）：
 * ok = reason 为 null；失败时 message 供 ACTION_REJECT 人类可读提示；
 * 成功时 detail 为 ACTION_DONE 的中立结果摘要（不含任何隐藏信息）。
 */
public record SplendorActionResult(
    SplendorFailReason reason,
    String message,
    Map<String, Object> detail
) {

    public boolean isOk() {
        return reason == null;
    }

    public static SplendorActionResult ok(Map<String, Object> detail) {
        return new SplendorActionResult(null, null, detail);
    }

    public static SplendorActionResult fail(SplendorFailReason reason, String message) {
        return new SplendorActionResult(reason, message, null);
    }
}
