package org.dromara.web.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.enums.PushSourceEnum;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.dromara.system.domain.SysMessage;
import org.dromara.system.mapper.SysMessageMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统版本更新自动通知。
 *
 * <p>应用启动后检查当前版本是否已广播过「版本更新」系统通知；没有则通过 RuoYi 原生
 * MessageService 向全体在线用户广播，并落入 sys_message 供消息中心（/resource/message/box）读取。
 * 版本号来自 Maven 资源过滤注入的 {@code project.version}，与 pom.xml 的 revision 保持一致。
 *
 * <p>去重策略：以固定标题 + 版本号在 sys_message 中精确匹配，命中即跳过，
 * 保证同一版本重启多少次都只通知一次；新版本号则自动发一条。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class VersionNoticeRunner implements ApplicationRunner {

    private static final String SOURCE = "version";
    private static final String TITLE_PREFIX = "DiceGo 版本更新 · v";

    private final MessageService messageService;
    private final SysMessageMapper messageMapper;

    @Value("${dicego.version:unknown}")
    private String projectVersion;

    @Override
    public void run(ApplicationArguments args) {
        String version = normalizeVersion(projectVersion);
        if (StringUtils.isBlank(version) || "unknown".equals(version)) {
            log.info("版本更新通知跳过：无法解析项目版本号");
            return;
        }
        String title = TITLE_PREFIX + version;
        try {
            Long exists = messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getSource, SOURCE)
                .eq(SysMessage::getTitle, title));
            if (exists != null && exists > 0) {
                log.info("版本更新通知跳过：v{} 已通知过", version);
                return;
            }
            Map<String, Object> data = Map.of(
                "version", version,
                "title", title,
                "content", "DiceGo 已更新至 v" + version + "，本次更新带来了体验优化与问题修复。"
            );
            PushPayloadDTO payload = PushPayloadDTO.of(
                PushTypeEnum.NOTICE.getType(), SOURCE, title, data);
            payload.setPath("");
            messageService.publishAll(payload);
            log.info("版本更新通知已广播：v{}", version);
        } catch (Exception e) {
            // 通知失败绝不影响应用启动
            log.warn("版本更新通知发送失败（不影响启动）: {}", e.getMessage());
        }
    }

    /** 去掉可能的构建后缀，保留 x.y.z 主版本号 */
    private static String normalizeVersion(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        // 兼容 "6.0.0" / "版本号: 6.0.0" 两种写法
        int colon = v.lastIndexOf(':');
        if (colon >= 0) v = v.substring(colon + 1).trim();
        return v;
    }
}
