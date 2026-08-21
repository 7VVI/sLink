package com.shortlink.common.constant;

import java.util.regex.Pattern;

/**
 * 短链系统通用常量。
 */
public final class ShortLinkConstants {

    /**
     * 短链状态：正常。
     */
    public static final int STATUS_ENABLED = 1;

    /**
     * 短链状态：已下线。
     */
    public static final int STATUS_DISABLED = 0;

    /**
     * 短链状态：已删除（逻辑删除）。
     */
    public static final int STATUS_DELETED = 2;

    /**
     * 用户状态：正常。
     */
    public static final int USER_STATUS_ENABLED = 1;

    /**
     * 角色：管理员。
     */
    public static final String ROLE_ADMIN = "ADMIN";

    /**
     * 角色：普通用户。
     */
    public static final String ROLE_USER = "USER";

    /**
     * 登录会话中缓存角色信息的 key。
     */
    public static final String SESSION_ROLE = "role";

    /**
     * 短码格式：7 位 Base62。
     */
    public static final Pattern SHORT_CODE_PATTERN = Pattern.compile("^[0-9A-Za-z]{7}$");

    private ShortLinkConstants() {
    }
}
