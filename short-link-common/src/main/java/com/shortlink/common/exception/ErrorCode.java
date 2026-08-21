package com.shortlink.common.exception;

/**
 * 统一错误码：code 五位数字，前三位对应 HTTP 语义段。
 */
public enum ErrorCode {

    SUCCESS(0, "成功", 200),

    PARAM_INVALID(40001, "参数校验失败", 400),

    UNAUTHORIZED(40100, "未登录或登录已过期", 401),

    FORBIDDEN(40300, "无权访问该资源", 403),

    NOT_FOUND(40400, "资源不存在", 404),

    TOO_MANY_REQUESTS(42900, "请求过于频繁，请稍后再试", 429),

    URL_INVALID(41001, "长链接不合法", 400),

    URL_BLACKLISTED(41002, "长链接域名命中黑名单", 400),

    USERNAME_EXISTS(42001, "用户名已存在", 400),

    LOGIN_FAILED(42002, "用户名或密码错误", 400),

    GROUP_NOT_FOUND(43001, "分组不存在", 404),

    GROUP_NAME_EXISTS(43002, "分组名已存在", 400),

    DOMAIN_INVALID(43003, "域名不合法，需为 http/https 前缀且不含路径", 400),

    DOMAIN_NOT_FOUND(43004, "域名不存在或已停用", 404),

    DOMAIN_EXISTS(43005, "域名已存在", 400),

    DOMAIN_IS_DEFAULT(43006, "默认域名不允许删除或停用", 400),

    LINK_NOT_IN_RECYCLE(44001, "短链不在回收站中", 400),

    SYSTEM_ERROR(50000, "系统内部错误", 500),

    ID_GENERATOR_BUSY(50001, "发号器繁忙，请稍后重试", 503);

    private final int code;

    private final String message;

    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
