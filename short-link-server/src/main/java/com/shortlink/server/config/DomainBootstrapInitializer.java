package com.shortlink.server.config;

import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.util.UrlValidator;
import com.shortlink.core.dal.entity.SurlDomainDO;
import com.shortlink.core.dal.mapper.SurlDomainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动引导：域名表为空时，以 http://localhost:{server.port} 写入默认域名，
 * 保证全新部署开箱可用；生产环境请通过域名管理接口替换默认域名。
 */
@Component
public class DomainBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DomainBootstrapInitializer.class);

    private final SurlDomainMapper domainMapper;

    private final String bootstrapDomain;

    public DomainBootstrapInitializer(SurlDomainMapper domainMapper,
                                      @Value("${server.port:8080}") int serverPort) {
        this.domainMapper = domainMapper;
        this.bootstrapDomain = "http://localhost:" + serverPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = domainMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        UrlValidator.requireValidDomainPrefix(bootstrapDomain);
        SurlDomainDO record = new SurlDomainDO();
        record.setDomain(bootstrapDomain);
        record.setName("默认域名（启动引导，可在域名管理中替换）");
        record.setIsDefault(true);
        record.setStatus(ShortLinkConstants.STATUS_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        domainMapper.insert(record);
        log.info("已初始化默认短链域名: {}（请通过域名管理替换为正式域名）", bootstrapDomain);
    }
}
