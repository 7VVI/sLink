package com.shortlink.core.id;

import com.shortlink.core.config.ShortLinkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 号段发号器单元测试：以内存版 leaf_alloc（max_id 原子推进）模拟 DB，
 * 不使用 Mockito（其跨线程 stub 调用延迟会干扰并发路径的时序）。
 */
class SegmentIdGeneratorTest {

    private static final long INITIAL_MAX_ID = 1_000_000_000L;

    private static final long STEP = 100L;

    private final AtomicLong maxIdInDb = new AtomicLong(INITIAL_MAX_ID);

    private SegmentIdGenerator generator;

    @BeforeEach
    void setUp() {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, methodArgs) -> {
                    if ("getLong".equals(method.getName())) {
                        return "max_id".equals(methodArgs[0]) ? maxIdInDb.get() : STEP;
                    }
                    return 0;
                });

        JdbcTemplate jdbcTemplate = new JdbcTemplate() {
            @Override
            public int update(String sql, Object... args) {
                maxIdInDb.addAndGet(STEP);
                return 1;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
                try {
                    return (T) rowMapper.mapRow(resultSet, 0);
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        ShortLinkProperties properties = new ShortLinkProperties();
        properties.getIdGenerator().setPreloadThreshold(0.2);
        generator = new SegmentIdGenerator(jdbcTemplate, properties);
    }

    @Test
    void nextIdShouldBeMonotonicAndUnique() {
        generator.initialize();
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(ids.add(generator.nextId()));
        }
        assertEquals(10_000, ids.size());
    }

    @Test
    void nextIdShouldBeUniqueUnderConcurrency() throws Exception {
        generator.initialize();
        int threads = 8;
        int perThread = 5_000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals((long) threads * perThread, ids.size());
    }

    @Test
    void idsShouldCrossSegmentBoundary() {
        generator.initialize();
        // 步长 100，连续取 5000 个必然跨越多个号段且保持严格递增
        long first = generator.nextId();
        long last = first;
        for (int i = 0; i < 5_000; i++) {
            long id = generator.nextId();
            assertTrue(id > last, "id 必须严格递增");
            last = id;
        }
        assertTrue(last - first >= 5_000);
    }
}
