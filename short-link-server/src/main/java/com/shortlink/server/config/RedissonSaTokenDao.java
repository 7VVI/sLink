package com.shortlink.server.config;

import cn.dev33.satoken.dao.SaTokenDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token 会话存储：基于 Redisson（String 编码）+ Jackson 序列化。
 *
 * <p>多实例部署时登录态共享；Redis 不可用时 Sa-Token 抛出异常由全局异常处理兜底。</p>
 */
public class RedissonSaTokenDao implements SaTokenDao {

    private final RedissonClient redisson;

    private final ObjectMapper objectMapper;

    public RedissonSaTokenDao(RedissonClient redisson) {
        this.redisson = redisson;
        this.objectMapper = new ObjectMapper();
        // SaSession 存在仅序列化用途的只读属性（如 timeout），反序列化需忽略未知字段
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.activateDefaultTyping(
                com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
    }

    @Override
    public String get(String key) {
        return bucket(key).get();
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            bucket(key).set(value);
        } else {
            bucket(key).set(value, Duration.ofSeconds(timeout));
        }
    }

    @Override
    public void update(String key, String value) {
        bucket(key).set(value);
    }

    @Override
    public void delete(String key) {
        bucket(key).delete();
    }

    @Override
    public long getTimeout(String key) {
        return millisToSeconds(bucket(key).remainTimeToLive());
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        RBucket<String> b = bucket(key);
        if (timeout == NEVER_EXPIRE) {
            b.clearExpire();
        } else {
            b.expire(Duration.ofSeconds(timeout));
        }
    }

    @Override
    public Object getObject(String key) {
        String json = bucket(key).get();
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("Sa-Token 会话反序列化失败: " + key, e);
        }
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        try {
            set(key, objectMapper.writeValueAsString(object), timeout);
        } catch (Exception e) {
            throw new IllegalStateException("Sa-Token 会话序列化失败: " + key, e);
        }
    }

    @Override
    public void updateObject(String key, Object object) {
        try {
            update(key, objectMapper.writeValueAsString(object));
        } catch (Exception e) {
            throw new IllegalStateException("Sa-Token 会话序列化失败: " + key, e);
        }
    }

    @Override
    public void deleteObject(String key) {
        delete(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return getTimeout(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        updateTimeout(key, timeout);
    }

    /**
     * 模糊检索：以 prefix 开头且包含 keyword 的 key，按 key 或 value 排序后分页。
     */
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortValue) {
        if (size <= 0) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        redisson.getKeys().getKeysByPattern(prefix + "*" + keyword + "*").forEach(keys::add);
        if (sortValue) {
            keys.sort(Comparator.comparing(k -> {
                String value = get(k);
                return value == null ? "" : value;
            }));
        } else {
            Collections.sort(keys);
        }
        if (start >= keys.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(start + size, keys.size());
        return new ArrayList<>(keys.subList(start, end));
    }

    private RBucket<String> bucket(String key) {
        return redisson.getBucket(key, StringCodec.INSTANCE);
    }

    /**
     * Redis TTL（毫秒）转 Sa-Token 语义：-1 永不过期、-2 不存在。
     */
    private long millisToSeconds(long millis) {
        if (millis == -1) {
            return NEVER_EXPIRE;
        }
        if (millis == -2) {
            return NOT_VALUE_EXPIRE;
        }
        return TimeUnit.MILLISECONDS.toSeconds(millis);
    }
}
