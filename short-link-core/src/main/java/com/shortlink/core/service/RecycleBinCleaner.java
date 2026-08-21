package com.shortlink.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回收站自动清理任务：每天将超过保留天数（默认 30 天）的删除短链物理清除。
 * 运行在调度线程，允许阻塞；也可通过管理端接口手动触发。
 */
@Component
public class RecycleBinCleaner {

    private static final Logger log = LoggerFactory.getLogger(RecycleBinCleaner.class);

    private final ShortLinkService shortLinkService;

    public RecycleBinCleaner(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @Scheduled(cron = "${shortlink.recycle-bin.purge-cron:0 30 3 * * ?}")
    public void purge() {
        try {
            shortLinkService.purgeExpired();
        } catch (Exception e) {
            log.error("回收站自动清理失败", e);
        }
    }
}
