package com.shortlink.server.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.constant.ShortLinkConstants;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色数据源：角色在登录时写入会话（见 AuthController），
 * 此处从会话读取，避免每次鉴权回源数据库。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 权限模型仅区分角色，暂不细化到操作权限
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        Object role = session.get(ShortLinkConstants.SESSION_ROLE);
        return role == null ? Collections.emptyList() : List.of(String.valueOf(role));
    }
}
