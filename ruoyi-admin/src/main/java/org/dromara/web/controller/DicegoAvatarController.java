package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * DiceGo 头像上传。
 *
 * <p>不依赖 OSS 服务配置（当前项目未配置 MinIO/S3）。做法是：把图片写到本地目录，
 * 再往 {@code sys_oss} 插入一条记录（url 指向本控制器的读取接口），并把该 ossId
 * 写进 {@code sys_user.avatar}。这样 RuoYi 自带的
 * {@code @Translation(OSS_ID_TO_URL)} 会在 getProfile 时自动翻译成可访问 URL，
 * 前端头像展示完全复用既有链路。</p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/avatar")
public class DicegoAvatarController {

    /** 单张头像上限 2MB */
    private static final long MAX_SIZE = 2L * 1024 * 1024;

    /** 允许的图片后缀 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 后缀到 Content-Type 的映射 */
    private static final Map<String, String> MIME = Map.of(
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "gif", "image/gif",
        "webp", "image/webp"
    );

    private final SysOssMapper ossMapper;
    private final ISysUserService userService;

    /** 头像本地存储目录，可用 ${dicego.avatar.dir} 覆盖 */
    @Value("${dicego.avatar.dir:./dicego-data/avatar}")
    private String avatarDir;

    /**
     * 上传头像并设为当前用户头像。
     *
     * <p>前端拿到返回的 url 后调用 fetchProfile 刷新，即可从 avatarUrl 拿到图片地址。</p>
     */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail("文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            return R.fail("头像不能超过 2MB");
        }

        String original = StrUtil.blankToDefault(file.getOriginalFilename(), "avatar");
        String ext = FileUtil.extName(original).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            return R.fail("仅支持 jpg / png / gif / webp 图片");
        }

        long ossId = IdWorker.getId();
        String fileName = ossId + "." + ext;
        Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).normalize();
            // 防目录穿越：确保目标仍在头像目录内
            if (!target.startsWith(dir)) {
                return R.fail("非法文件名");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            return R.fail("头像保存失败：" + e.getMessage());
        }

        // 写一条 sys_oss 记录，让 RuoYi 的 @Translation(OSS_ID_TO_URL) 能翻译出 URL
        SysOss oss = new SysOss();
        oss.setOssId(ossId);
        oss.setFileName(fileName);
        oss.setOriginalName(original);
        oss.setFileSuffix(ext);
        oss.setUrl("/api/avatar/" + ossId);
        oss.setService("dicego-local");
        ossMapper.insert(oss);

        // 走 RuoYi 自带的 profile 更新链路，把 ossId 写进 sys_user.avatar
        // 与 SysProfileController 保持一致：忽略数据权限，避免头像更新被角色数据范围拦截
        SysUserBo user = new SysUserBo();
        user.setUserId(LoginHelper.getUserId());
        user.setAvatar(ossId);
        DataPermissionHelper.ignore(() -> userService.updateUserProfile(user));

        return R.ok(Map.of("ossId", ossId, "url", "/api/avatar/" + ossId));
    }

    /**
     * 读取头像图片。
     *
     * <p>sys_oss.url 指向本接口，而浏览器 img 标签不会携带 token，故跳过登录校验。</p>
     */
    @SaIgnore
    @GetMapping("/{ossId}")
    public void image(@PathVariable Long ossId, HttpServletResponse response) throws IOException {
        SysOss oss = ossMapper.selectById(ossId);
        if (oss == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
        Path file = dir.resolve(oss.getFileName()).normalize();
        if (!file.startsWith(dir) || !Files.exists(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(MIME.getOrDefault(oss.getFileSuffix(), "application/octet-stream"));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
        Files.copy(file, response.getOutputStream());
    }
}
