package com.shortlink.core.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短链记录（逻辑表 short_url，由 ShardingSphere 按 short_code 哈希路由到 ds0-3 × short_url_0-15）。
 *
 * <p>主键为发号器产生的全局 ID，插入前必须填充。</p>
 */
@TableName("short_url")
public class ShortUrlDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 发号器全局 ID（IdType.INPUT：由应用侧号段发号器赋值）。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 短码（7 位 Base62），分片键。
     */
    private String shortCode;

    private String longUrl;

    private String title;

    private Long userId;

    /**
     * 过期时间，null 表示永不过期。
     */
    private LocalDateTime expireTime;

    /**
     * 1-正常 0-下线 2-删除。
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
