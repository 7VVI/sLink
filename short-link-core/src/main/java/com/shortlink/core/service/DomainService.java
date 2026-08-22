package com.shortlink.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.CreateDomainReq;
import com.shortlink.common.dto.DomainVO;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.common.util.UrlValidator;
import com.shortlink.core.dal.entity.SurlDomainDO;
import com.shortlink.core.dal.mapper.SurlDomainMapper;
import com.shortlink.core.support.Reactors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 域名领域服务：管理端增删改查，创建短链时解析可用域名。
 * 域名表（surl_domain）是短链域名的唯一事实来源。
 *
 * <p>默认域名全局唯一（is_default=1），不允许删除或停用；
 * 跳转与域名无关，短码在任意已配置域名下均可解析。</p>
 */
@Service
public class DomainService {

    private final SurlDomainMapper domainMapper;

    public DomainService(SurlDomainMapper domainMapper) {
        this.domainMapper = domainMapper;
    }

    /**
     * 新增域名。
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<DomainVO> create(CreateDomainReq request) {
        return Reactors.call(() -> {
            String normalized = normalize(request.domain());
            UrlValidator.requireValidDomainPrefix(normalized);
            if (domainMapper.selectCount(new LambdaQueryWrapper<SurlDomainDO>()
                    .eq(SurlDomainDO::getDomain, normalized)) > 0) {
                throw new BizException(ErrorCode.DOMAIN_EXISTS);
            }
            boolean setDefault = Boolean.TRUE.equals(request.isDefault());
            SurlDomainDO domain = new SurlDomainDO();
            domain.setDomain(normalized);
            domain.setName(request.name());
            domain.setIsDefault(setDefault);
            domain.setStatus(ShortLinkConstants.STATUS_ENABLED);
            LocalDateTime now = LocalDateTime.now();
            domain.setCreateTime(now);
            domain.setUpdateTime(now);
            domainMapper.insert(domain);
            if (setDefault) {
                clearOtherDefaults(domain.getId());
            }
            return toVO(domain);
        });
    }

    /**
     * 管理端域名列表（全部）。
     */
    public Mono<List<DomainVO>> listAll() {
        return Reactors.call(() -> domainMapper.selectList(new LambdaQueryWrapper<SurlDomainDO>()
                        .orderByDesc(SurlDomainDO::getIsDefault)
                        .orderByAsc(SurlDomainDO::getId))
                .stream().map(this::toVO).toList());
    }

    /**
     * 用户可选域名列表（仅启用）。
     */
    public Mono<List<DomainVO>> listEnabled() {
        return Reactors.call(() -> domainMapper.selectList(new LambdaQueryWrapper<SurlDomainDO>()
                        .eq(SurlDomainDO::getStatus, ShortLinkConstants.STATUS_ENABLED)
                        .orderByDesc(SurlDomainDO::getIsDefault)
                        .orderByAsc(SurlDomainDO::getId))
                .stream().map(this::toVO).toList());
    }

    /**
     * 上线/下线域名（默认域名不允许下线）。
     */
    public Mono<Void> changeStatus(long domainId, boolean enabled) {
        return Reactors.call(() -> {
            SurlDomainDO domain = requireExists(domainId);
            boolean isDefault = Boolean.TRUE.equals(domain.getIsDefault());
            if (isDefault && !enabled) {
                throw new BizException(ErrorCode.DOMAIN_IS_DEFAULT);
            }
            SurlDomainDO update = new SurlDomainDO();
            update.setId(domain.getId());
            update.setStatus(enabled ? ShortLinkConstants.STATUS_ENABLED : ShortLinkConstants.STATUS_DISABLED);
            update.setUpdateTime(LocalDateTime.now());
            domainMapper.updateById(update);
            return null;
        }).then();
    }

    /**
     * 设为默认域名（原默认域名自动取消默认标记）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<Void> setDefault(long domainId) {
        return Reactors.call(() -> {
            SurlDomainDO domain = requireExists(domainId);
            if (domain.getStatus() != ShortLinkConstants.STATUS_ENABLED) {
                throw new BizException(ErrorCode.DOMAIN_NOT_FOUND, "停用域名不能设为默认");
            }
            SurlDomainDO update = new SurlDomainDO();
            update.setId(domain.getId());
            update.setIsDefault(true);
            update.setUpdateTime(LocalDateTime.now());
            domainMapper.updateById(update);
            clearOtherDefaults(domain.getId());
            return null;
        }).then();
    }

    /**
     * 删除域名（默认域名不允许删除）。
     */
    public Mono<Void> remove(long domainId) {
        return Reactors.call(() -> {
            SurlDomainDO domain = requireExists(domainId);
            if (Boolean.TRUE.equals(domain.getIsDefault())) {
                throw new BizException(ErrorCode.DOMAIN_IS_DEFAULT);
            }
            domainMapper.deleteById(domain.getId());
            return null;
        }).then();
    }

    /**
     * 创建短链时解析域名：domainId 为空取默认域名；无任何可用域名时抛出业务异常。
     */
    public SurlDomainDO resolveForCreate(Long domainId) {
        if (domainId != null && domainId != 0L) {
            SurlDomainDO domain = domainMapper.selectById(domainId);
            if (domain == null || domain.getStatus() != ShortLinkConstants.STATUS_ENABLED) {
                throw new BizException(ErrorCode.DOMAIN_NOT_FOUND, "域名不存在或已停用: " + domainId);
            }
            return domain;
        }
        SurlDomainDO domain = defaultDomain();
        if (domain == null) {
            throw new BizException(ErrorCode.DOMAIN_NOT_FOUND, "暂无可用默认域名，请先在域名管理中配置");
        }
        return domain;
    }

    /**
     * 当前启用的默认域名，无则返回 null。
     */
    public SurlDomainDO defaultDomain() {
        return domainMapper.selectOne(new LambdaQueryWrapper<SurlDomainDO>()
                .eq(SurlDomainDO::getIsDefault, true)
                .eq(SurlDomainDO::getStatus, ShortLinkConstants.STATUS_ENABLED)
                .last("LIMIT 1"));
    }

    private SurlDomainDO requireExists(long domainId) {
        SurlDomainDO domain = domainMapper.selectById(domainId);
        if (domain == null) {
            throw new BizException(ErrorCode.DOMAIN_NOT_FOUND, "域名不存在: " + domainId);
        }
        return domain;
    }

    private void clearOtherDefaults(long keepDomainId) {
        domainMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SurlDomainDO>()
                .eq(SurlDomainDO::getIsDefault, true)
                .ne(SurlDomainDO::getId, keepDomainId)
                .set(SurlDomainDO::getIsDefault, false)
                .set(SurlDomainDO::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 去掉末尾斜杠，保留协议与主机（含端口）。
     */
    private String normalize(String domain) {
        String trimmed = domain == null ? "" : domain.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private DomainVO toVO(SurlDomainDO domain) {
        return new DomainVO(domain.getId(), domain.getDomain(), domain.getName(),
                Boolean.TRUE.equals(domain.getIsDefault()), domain.getStatus(), domain.getCreateTime());
    }
}
