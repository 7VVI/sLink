package com.shortlink.core.id;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.core.config.ShortLinkProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Leaf-Segment 号段模式发号器（双 Buffer 预加载）。
 *
 * <p>取号 {@link #nextId()} 为纯内存 CAS 操作；消耗到阈值后由独立线程异步
 * 预加载下一段，DB 交互频率 ≈ step/QPS。号段耗尽且下一段未就绪时同步阻塞
 * 等待，超时抛出业务异常。</p>
 */
@Component
public class SegmentIdGenerator implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SegmentIdGenerator.class);

    private final JdbcTemplate jdbcTemplate;

    private final ShortLinkProperties.IdGenerator config;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition nextSegmentReady = lock.newCondition();

    private final AtomicBoolean loadingNext = new AtomicBoolean(false);

    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "segment-id-loader");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Segment current;

    private volatile Segment next;

    public SegmentIdGenerator(JdbcTemplate jdbcTemplate, ShortLinkProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = properties.getIdGenerator();
    }

    @PostConstruct
    public void initialize() {
        this.current = fetchSegment();
        log.info("号段发号器初始化完成: bizTag={}, current=[{}, {}]",
                config.getBizTag(), current.min, current.max);
    }

    /**
     * 获取下一个全局唯一 ID。线程安全，可在并发环境直接调用。
     */
    public long nextId() {
        while (true) {
            Segment cur = this.current;
            long id = cur.next();
            if (id <= cur.max) {
                if (cur.idleRatio() < config.getPreloadThreshold()) {
                    asyncLoadNext();
                }
                return id;
            }
            if (!awaitNextSegment(cur)) {
                throw new BizException(ErrorCode.ID_GENERATOR_BUSY);
            }
        }
    }

    /**
     * 阻塞等待下一段就绪并完成切换；返回 false 表示等待超时。
     */
    private boolean awaitNextSegment(Segment exhausted) {
        long deadlineNanos = System.nanoTime() + config.getNextSegmentWaitTimeout().toNanos();
        lock.lock();
        try {
            while (current == exhausted) {
                if (next != null) {
                    current = next;
                    next = null;
                    return true;
                }
                asyncLoadNext();
                long restNanos = deadlineNanos - System.nanoTime();
                if (restNanos <= 0) {
                    log.error("等待下一号段超时: bizTag={}", config.getBizTag());
                    return false;
                }
                try {
                    nextSegmentReady.await(restNanos, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            // 其他线程已完成切换
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 异步预加载下一段；已有加载任务在途或已有下一段时直接返回。
     */
    private void asyncLoadNext() {
        if (next != null || !loadingNext.compareAndSet(false, true)) {
            return;
        }
        loader.execute(() -> {
            try {
                Segment segment = fetchSegment();
                lock.lock();
                try {
                    next = segment;
                    nextSegmentReady.signalAll();
                } finally {
                    lock.unlock();
                }
                log.debug("号段预加载完成: [{}, {}]", segment.min, segment.max);
            } catch (Exception e) {
                // 加载失败后 loadingNext 复位，后续取号会再次触发重试
                log.error("号段预加载失败: bizTag={}", config.getBizTag(), e);
            } finally {
                loadingNext.set(false);
            }
        });
    }

    /**
     * 从 DB 拉取一个号段：先原子推进 max_id，再读取推进后的窗口。
     */
    private Segment fetchSegment() {
        jdbcTemplate.update("UPDATE leaf_alloc SET max_id = max_id + step WHERE biz_tag = ?",
                config.getBizTag());
        return jdbcTemplate.queryForObject(
                "SELECT max_id, step FROM leaf_alloc WHERE biz_tag = ?",
                (rs, rowNum) -> {
                    long max = rs.getLong("max_id");
                    long step = rs.getLong("step");
                    return new Segment(max - step + 1, max);
                },
                config.getBizTag());
    }

    @Override
    public void destroy() {
        loader.shutdownNow();
    }

    /**
     * 号段窗口 [min, max]，seq 从 min-1 开始自增。
     */
    private static final class Segment {

        final long min;

        final long max;

        final AtomicLong seq;

        Segment(long min, long max) {
            this.min = min;
            this.max = max;
            this.seq = new AtomicLong(min - 1);
        }

        long next() {
            return seq.incrementAndGet();
        }

        double idleRatio() {
            long idle = max - seq.get();
            return (double) idle / (max - min + 1);
        }
    }
}
