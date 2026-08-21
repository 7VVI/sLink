package com.shortlink.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * core 模块通用配置入口。
 */
@Configuration
@EnableConfigurationProperties(ShortLinkProperties.class)
public class CoreConfiguration {
}
