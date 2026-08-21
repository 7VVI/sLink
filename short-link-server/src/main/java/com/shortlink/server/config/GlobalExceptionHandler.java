package com.shortlink.server.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.resource.NoResourceFoundException;

/**
 * 全局异常处理：业务异常按错误码映射 HTTP 状态，统一返回 Result 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(Result.fail(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public ResponseEntity<Result<Void>> handleNoPermission(Exception e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(Result.fail(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Result<Void>> handleBindException(WebExchangeBindException e) {
        String message = e.getFieldErrors().isEmpty()
                ? ErrorCode.PARAM_INVALID.getMessage()
                : e.getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(Result.fail(ErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(Result.fail(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> handleThrowable(Throwable e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYSTEM_ERROR));
    }
}
