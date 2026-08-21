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
