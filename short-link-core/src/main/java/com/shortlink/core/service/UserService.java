package com.shortlink.core.service;

import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.LoginReq;
import com.shortlink.common.dto.RegisterReq;
import com.shortlink.common.dto.UserInfoVO;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.core.dal.entity.UserDO;
import com.shortlink.core.dal.mapper.UserMapper;
import com.shortlink.core.support.Reactors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 用户领域服务：注册、登录校验、信息查询。认证状态托管给 Sa-Token。
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 注册新用户，返回用户 ID。
     */
    public Mono<Long> register(RegisterReq request) {
        return Reactors.call(() -> {
            if (userMapper.selectByUsername(request.username()) != null) {
                throw new BizException(ErrorCode.USERNAME_EXISTS);
            }
            UserDO user = new UserDO();
            user.setUsername(request.username());
            user.setPassword(passwordEncoder.encode(request.password()));
            user.setNickname(request.nickname() == null || request.nickname().isBlank()
                    ? request.username() : request.nickname());
            user.setRole(ShortLinkConstants.ROLE_USER);
            user.setStatus(ShortLinkConstants.USER_STATUS_ENABLED);
            LocalDateTime now = LocalDateTime.now();
            user.setCreateTime(now);
            user.setUpdateTime(now);
            userMapper.insert(user);
            return user.getId();
        });
    }

    /**
     * 登录校验，成功返回用户（密码已校验），失败抛出业务异常。
     */
    public Mono<UserDO> verifyLogin(LoginReq request) {
        return Reactors.call(() -> {
            UserDO user = userMapper.selectByUsername(request.username());
            if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new BizException(ErrorCode.LOGIN_FAILED);
            }
            if (user.getStatus() == null || user.getStatus() != ShortLinkConstants.USER_STATUS_ENABLED) {
                throw new BizException(ErrorCode.LOGIN_FAILED, "账号已被禁用");
            }
            return user;
        });
    }

    /**
     * 查询用户信息。
     */
    public Mono<UserInfoVO> userInfo(long userId) {
        return Reactors.call(() -> {
            UserDO user = userMapper.selectById(userId);
            if (user == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
            }
            return new UserInfoVO(user.getId(), user.getUsername(), user.getNickname(),
                    user.getRole(), user.getCreateTime());
        });
    }

    /**
     * 查询用户角色（供 Sa-Token StpInterface 使用）。
     */
    public String roleOf(UserDO user) {
        return user == null || user.getRole() == null ? ShortLinkConstants.ROLE_USER : user.getRole();
    }
}
