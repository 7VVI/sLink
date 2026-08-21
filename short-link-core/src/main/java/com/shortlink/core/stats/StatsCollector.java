package com.shortlink.core.stats;

import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.shortlink.core.config.ShortLinkProperties;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 点击统计收集器：跳转线程仅做 RingBuffer 发布（纳秒级），由独立消费线程批量刷 Redis。
 */
@Component
public class StatsCollector implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StatsCollector.class);

    private final Disruptor<ClickEvent> disruptor;

    private final StatsFlushHandler flushHandler;

    public StatsCollector(RedissonClient redisson, ShortLinkProperties properties) {
        ShortLinkProperties.Stats stats = properties.getStats();
        this.flushHandler = new StatsFlushHandler(redisson, stats.getBatchSize(),
                stats.getFlushInterval().toMillis());
        this.disruptor = new Disruptor<>(
                ClickEvent::new,
                stats.getRingBufferSize(),
                r -> {
                    Thread thread = new Thread(r, "stats-consumer");
                    thread.setDaemon(true);
                    return thread;
                },
                ProducerType.MULTI,
                new SleepingWaitStrategy());
        disruptor.handleEventsWith(flushHandler);
        disruptor.start();
        log.info("统计收集器启动: ringBuffer={}, batchSize={}, flushInterval={}ms",
                stats.getRingBufferSize(), stats.getBatchSize(), stats.getFlushInterval().toMillis());
    }

    /**
     * 发布一条点击事件。环满时 next() 短暂阻塞形成背压，不影响统计正确性。
     */
    public void publish(String code, String visitorId) {
        long sequence = disruptor.getRingBuffer().next();
        try {
            ClickEvent event = disruptor.getRingBuffer().get(sequence);
            event.code = code;
            event.visitorId = visitorId;
        } finally {
            disruptor.getRingBuffer().publish(sequence);
        }
    }

    @Override
    public void destroy() throws Exception {
        try {
            // 排空环内未消费事件，尽量不丢尾部数据
            disruptor.shutdown(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            disruptor.halt();
        }
        flushHandler.flush();
    }
}
