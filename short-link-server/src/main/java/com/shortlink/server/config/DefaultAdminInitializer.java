package com.shortlink.server.config;

import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.core.dal.entity.UserDO;
import com.shortlink.core.dal.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动引导：用户表为空时创建默认管理员（admin / admin123），生产环境请立即改密。
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DefaultAdminInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        UserDO admin = new UserDO();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setNickname("默认管理员");
        admin.setRole(ShortLinkConstants.ROLE_ADMIN);
        admin.setStatus(ShortLinkConstants.USER_STATUS_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        admin.setCreateTime(now);
        admin.setUpdateTime(now);
        userMapper.insert(admin);
        log.warn("已创建默认管理员: {} / {}，请登录后立即修改密码！", DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }
}
