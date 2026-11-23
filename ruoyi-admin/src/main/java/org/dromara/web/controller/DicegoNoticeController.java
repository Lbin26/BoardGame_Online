package org.dromara.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * 桌游平台系统通知发布（超管专用）。
 *
 * <p>不依赖 RuoYi 菜单权限：本平台用户未分配系统角色，故按用户名/昵称白名单校验超管，
 * 与前端 constants/admin.ts 的名单保持一致。发布走 RuoYi 原生 MessageService，
 * 广播写入 sys_message 并推送给全体在线用户，消息中心（/resource/message/box）可见。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/dicego/notice")
@RequiredArgsConstructor
public class DicegoNoticeController {

    /** 平台超级管理员名单（唯一超管：欧洲小跪族；userName / nickName 任一命中） */
    private static final Set<String> SUPER_ADMINS = Set.of("欧洲小跪族");

    private final MessageService messageService;

    /** 发布系统公告的请求体 */
    public record PublishBody(
        @NotBlank(message = "标题不能为空") @Size(max = 50, message = "标题不能超过 50 个字符") String title,
        @NotBlank(message = "内容不能为空") @Size(max = 1000, message = "内容不能超过 1000 个字符") String content
    ) {
    }

    /**
     * 超管发布系统通知，向全体用户广播并存的消息盒子。
     */
    @PostMapping("/publish")
    public R<Void> publish(@Valid @RequestBody PublishBody body) {
        if (!isSuperAdmin()) {
            return R.fail("仅平台超级管理员可发布系统通知");
        }
        String title = body.title().trim();
        String content = body.content().trim();
        Map<String, Object> data = Map.of(
            "title", title,
            "content", content,
            "noticeContent", content
        );
        PushPayloadDTO payload = PushPayloadDTO.of(
            PushTypeEnum.NOTICE.getType(), "notice", title, data);
        payload.setPath("");
        messageService.publishAll(payload);
        log.info("超管发布系统通知: title={}", title);
        return R.ok();
    }

    /** 当前登录用户是否为超管（按 userName 白名单） */
    private boolean isSuperAdmin() {
        try {
            String userName = LoginHelper.getUsername();
            return userName != null && SUPER_ADMINS.contains(userName);
        } catch (Exception e) {
            return false;
        }
    }
}
