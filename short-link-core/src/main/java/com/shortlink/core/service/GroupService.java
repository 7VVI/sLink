package com.shortlink.core.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.CreateGroupReq;
import com.shortlink.common.dto.GroupVO;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.exception.ErrorCode;
import com.shortlink.core.dal.entity.LinkGroupDO;
import com.shortlink.core.dal.entity.ShortUrlDO;
import com.shortlink.core.dal.mapper.LinkGroupMapper;
import com.shortlink.core.dal.mapper.ShortUrlMapper;
import com.shortlink.core.support.Reactors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分组领域服务：创建/重命名/删除分组，分组列表附带短链数。
 *
 * <p>删除分组时组内短链移动回“未分组”（group_id=0，全分片广播更新）。</p>
 */
@Service
public class GroupService {

    private final LinkGroupMapper linkGroupMapper;

    private final ShortUrlMapper shortUrlMapper;

    public GroupService(LinkGroupMapper linkGroupMapper, ShortUrlMapper shortUrlMapper) {
        this.linkGroupMapper = linkGroupMapper;
        this.shortUrlMapper = shortUrlMapper;
    }

    /**
     * 创建分组。
     */
    public Mono<GroupVO> create(CreateGroupReq request, long userId) {
        return Reactors.call(() -> {
            requireNameAvailable(request.name(), userId, null);
            LinkGroupDO group = new LinkGroupDO();
            group.setUserId(userId);
            group.setName(request.name().trim());
            LocalDateTime now = LocalDateTime.now();
            group.setCreateTime(now);
            group.setUpdateTime(now);
            linkGroupMapper.insert(group);
            return toVO(group, 0L);
        });
    }

    /**
     * 当前用户分组列表（含各分组下未删除短链数）。
     */
    public Mono<List<GroupVO>> list(long userId) {
        return Reactors.call(() -> {
            List<LinkGroupDO> groups = linkGroupMapper.selectList(new QueryWrapper<LinkGroupDO>()
                    .eq("user_id", userId)
                    .orderByAsc("create_time"));
            Map<Long, Long> counts = countByGroup(userId);
            return groups.stream()
                    .map(group -> toVO(group, counts.getOrDefault(group.getId(), 0L)))
                    .toList();
        });
    }

    /**
     * 重命名分组。
     */
    public Mono<Void> rename(long groupId, String name, long userId) {
        return Reactors.call(() -> {
            LinkGroupDO group = requireOwnedGroup(groupId, userId);
            requireNameAvailable(name.trim(), userId, group.getId());
            LinkGroupDO update = new LinkGroupDO();
            update.setId(group.getId());
            update.setName(name.trim());
            update.setUpdateTime(LocalDateTime.now());
            linkGroupMapper.updateById(update);
            return null;
        }).then();
    }

    /**
     * 删除分组：组内短链移回未分组后删除分组记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<Void> remove(long groupId, long userId) {
        return Reactors.call(() -> {
            LinkGroupDO group = requireOwnedGroup(groupId, userId);
            // 分片键不含 group_id，按 user_id+group_id 广播更新全部分片
            shortUrlMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ShortUrlDO>()
                            .eq(ShortUrlDO::getUserId, userId)
                            .eq(ShortUrlDO::getGroupId, groupId)
                            .set(ShortUrlDO::getGroupId, 0L)
                            .set(ShortUrlDO::getUpdateTime, LocalDateTime.now()));
            linkGroupMapper.deleteById(group.getId());
            return null;
        }).then();
    }

    /**
     * 校验分组归属（供短链服务复用），groupId 为 0（未分组）时直接放行。
     */
    public void requireGroupOwned(Long groupId, long userId) {
        if (groupId == null || groupId == 0L) {
            return;
        }
        requireOwnedGroup(groupId, userId);
    }

    private LinkGroupDO requireOwnedGroup(long groupId, long userId) {
        LinkGroupDO group = linkGroupMapper.selectById(groupId);
        if (group == null || group.getUserId() != userId) {
            throw new BizException(ErrorCode.GROUP_NOT_FOUND, "分组不存在: " + groupId);
        }
        return group;
    }

    private void requireNameAvailable(String name, long userId, Long excludeGroupId) {
        List<LinkGroupDO> conflicts = linkGroupMapper.selectList(new QueryWrapper<LinkGroupDO>()
                .eq("user_id", userId)
                .eq("name", name.trim()));
        boolean duplicated = conflicts.stream()
                .anyMatch(g -> excludeGroupId == null || g.getId() != excludeGroupId);
        if (duplicated) {
            throw new BizException(ErrorCode.GROUP_NAME_EXISTS);
        }
    }

    /**
     * 按分组统计未删除短链数（GROUP BY 跨分片归并，一次查询）。
     */
    private Map<Long, Long> countByGroup(long userId) {
        List<Map<String, Object>> rows = shortUrlMapper.selectMaps(new QueryWrapper<ShortUrlDO>()
                .select("group_id", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .ne("status", ShortLinkConstants.STATUS_DELETED)
                .groupBy("group_id"));
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object groupId = row.get("group_id");
            Object cnt = row.get("cnt");
            if (groupId instanceof Number g && cnt instanceof Number c) {
                counts.put(g.longValue(), c.longValue());
            }
        }
        return counts;
    }

    private GroupVO toVO(LinkGroupDO group, long linkCount) {
        return new GroupVO(group.getId(), group.getName(), linkCount, group.getCreateTime());
    }
}
