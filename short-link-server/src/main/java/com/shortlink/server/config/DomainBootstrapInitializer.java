package com.shortlink.server.config;

import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.util.UrlValidator;
import com.shortlink.core.config.ShortLinkProperties;
import com.shortlink.core.dal.entity.SurlDomainDO;
import com.shortlink.core.dal.mapper.SurlDomainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动引导：域名表为空时，将配置的 shortlink.domain 写入为默认域名，
 * 保证创建短链始终有可用域名。
 */
@Component
public class DomainBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DomainBootstrapInitializer.class);

    private final SurlDomainMapper domainMapper;

    private final ShortLinkProperties properties;

    public DomainBootstrapInitializer(SurlDomainMapper domainMapper, ShortLinkProperties properties) {
        this.domainMapper = domainMapper;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = domainMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        String domain = properties.getDomain().trim();
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        UrlValidator.requireValidDomainPrefix(domain);
        SurlDomainDO record = new SurlDomainDO();
        record.setDomain(domain);
        record.setName("默认域名");
        record.setIsDefault(true);
        record.setStatus(ShortLinkConstants.STATUS_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        domainMapper.insert(record);
        log.info("已初始化默认短链域名: {}", domain);
    }
}
