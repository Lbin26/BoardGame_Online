package org.dromara.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.dromara.system.api.model.LoginUser;
import org.dromara.system.domain.SysMessage;
import org.dromara.system.domain.vo.SysMessageVo;
import org.dromara.system.mapper.SysMessageMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 桌游平台系统通知发布（超管专用）。
 *
 * <p>超管判定走数据库角色表：登录用户的 {@code sys_role.role_key = 'superadmin'} 才可发布，
 * 与前端按 roleGroup 判定的口径一致，不再使用用户名/昵称硬编码白名单。
 * 发布走 RuoYi 原生 MessageService，广播写入 boardgame_online.sys_message 并推送给全体在线用户；
 * 列表接口直接查询同一张 sys_message 表，供邮件消息中心与大厅「平台消息」共用，未登录也可查看。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/dicego/notice")
@RequiredArgsConstructor
public class DicegoNoticeController {

    /** 平台超级管理员角色 key（与 sys_role.role_key 对应） */
    private static final String SUPER_ADMIN_ROLE_KEY = "superadmin";

    /** 平台公告来源标识（写入 sys_message.source） */
    private static final String SOURCE_DICEGO = "dicego";

    /** 版本更新来源标识（写入 sys_message.source） */
    private static final String SOURCE_VERSION = "version";

    /** 全局广播接收人标识（写入 sys_message.send_user_ids） */
    private static final String GLOBAL_USER_IDS = "0";

    /** 平台消息最多返回条数，避免历史公告无限堆积 */
    private static final long NOTICE_LIMIT = 20L;

    private final MessageService messageService;
    private final SysMessageMapper messageMapper;

    /** 发布系统公告的请求体 */
    public record PublishBody(
        @NotBlank(message = "标题不能为空") @Size(max = 50, message = "标题不能超过 50 个字符") String title,
        @NotBlank(message = "内容不能为空") @Size(max = 1000, message = "内容不能超过 1000 个字符") String content
    ) {
    }

    /**
     * 平台消息列表：直接查询 boardgame_online.sys_message。
     *
     * <p>只取 DiceGo 公告与版本更新两类全局广播，RuoYi 原生系统消息/工作流消息一律不返回，
     * 从数据源头保证邮件消息中心与大厅「平台消息」看到的是同一份内容。
     */
    @GetMapping("/list")
    public R<List<SysMessageVo>> list() {
        List<SysMessage> rows = messageMapper.lambda()
            .eq(SysMessage::getCategory, PushTypeEnum.NOTICE.getType())
            .in(SysMessage::getSource, SOURCE_DICEGO, SOURCE_VERSION)
            .eq(SysMessage::getSendUserIds, GLOBAL_USER_IDS)
            .orderByDesc(SysMessage::getCreateTime, SysMessage::getMessageId)
            .list(new Page<>(1, NOTICE_LIMIT, false));
        return R.ok(rows.stream().map(this::toVo).toList());
    }

    /** 消息实体转前端 VO，dataJson 还原为对象供前端读取 title/content */
    private SysMessageVo toVo(SysMessage entity) {
        SysMessageVo vo = MapstructUtils.convert(entity, SysMessageVo.class);
        if (vo == null) {
            vo = new SysMessageVo();
            vo.setMessageId(entity.getMessageId());
            vo.setCategory(entity.getCategory());
            vo.setType(entity.getType());
            vo.setSource(entity.getSource());
            vo.setTitle(entity.getTitle());
            vo.setMessage(entity.getMessage());
            vo.setContent(entity.getContent());
            vo.setPath(entity.getPath());
            vo.setCreateTime(entity.getCreateTime());
        }
        if (StringUtils.isNotBlank(entity.getDataJson())) {
            vo.setData(JsonUtils.parseObject(entity.getDataJson(), Object.class));
        }
        return vo;
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
        // source 用 DiceGo 专属标识，前端消息中心据此区分平台通知与 RuoYi 原生公告
        PushPayloadDTO payload = PushPayloadDTO.of(
            PushTypeEnum.NOTICE.getType(), SOURCE_DICEGO, title, data);
        payload.setPath("");
        messageService.publishAll(payload);
        log.info("超管发布系统通知: title={}", title);
        return R.ok();
    }

    /**
     * 当前登录用户是否为平台超管：完全由数据库角色决定。
     *
     * <p>用 {@code getRoles()}（登录时从 sys_user_role + sys_role 装载）而非
     * {@code getRolePermission()}——后者对框架内置 admin 用户做了硬编码兜底，
     * 会把不再拥有 superadmin 角色的 admin 也算作超管。
     */
    private boolean isSuperAdmin() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().stream()
                    .anyMatch(role -> SUPER_ADMIN_ROLE_KEY.equals(role.getRoleKey()));
        } catch (Exception e) {
            return false;
        }
    }
}
