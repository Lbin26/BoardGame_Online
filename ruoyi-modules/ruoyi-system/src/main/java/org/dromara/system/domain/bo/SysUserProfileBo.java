package org.dromara.system.domain.bo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dromara.common.core.constant.RegexConstants;
import org.dromara.common.core.xss.Xss;
import org.dromara.common.sensitive.annotation.Sensitive;
import org.dromara.common.sensitive.core.SensitiveStrategy;

import java.io.Serial;
import java.io.Serializable;

/**
 * 个人信息业务处理
 *
 * @author Michelle.Chung
 */

@Data
@NoArgsConstructor
public class SysUserProfileBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户昵称
     */
    @Xss(message = "用户昵称不能包含脚本字符")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过{max}个字符")
    private String nickName;

    /**
     * 用户邮箱
     */
    @Sensitive(strategy = SensitiveStrategy.EMAIL)
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 手机号码
     */
    @Pattern(regexp = RegexConstants.MOBILE, message = "手机号格式不正确")
    @Sensitive(strategy = SensitiveStrategy.PHONE)
    private String phoneNumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    private String gender;

    /**
     * 年龄（DiceGo 个人资料扩展）
     */
    @Min(value = 1, message = "年龄必须大于 0")
    @Max(value = 150, message = "年龄不能超过 150")
    private Integer age;

    /**
     * 偏好游戏类型分区 key，逗号分隔（DiceGo 个人资料扩展）
     */
    @Size(max = 255, message = "偏好游戏类型长度不能超过{max}个字符")
    private String favGameTypes;

    /**
     * 头像 OSS ID
     */
    private Long avatar;

}
