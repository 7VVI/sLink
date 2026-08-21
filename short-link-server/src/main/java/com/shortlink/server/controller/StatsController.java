package com.shortlink.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shortlink.common.constant.ShortLinkConstants;
import com.shortlink.common.dto.StatsHistoryVO;
import com.shortlink.common.dto.StatsVO;
import com.shortlink.common.result.Result;
import com.shortlink.core.dal.mapper.ShortUrlStatsMapper;
import com.shortlink.core.service.ShortLinkService;
import com.shortlink.core.stats.StatsQueryService;
import com.shortlink.core.support.Reactors;
import com.shortlink.server.support.SaTokenReactorContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计查询接口（需登录且为短链属主或管理员）。
 */
@Tag(name = "统计")
@Validated
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsQueryService statsQueryService;

    private final ShortUrlStatsMapper statsMapper;

    private final ShortLinkService shortLinkService;

    public StatsController(StatsQueryService statsQueryService,
                           ShortUrlStatsMapper statsMapper,
                           ShortLinkService shortLinkService) {
        this.statsQueryService = statsQueryService;
        this.statsMapper = statsMapper;
        this.shortLinkService = shortLinkService;
    }

    @Operation(summary = "实时统计（当日 PV/UV、累计 PV）")
    @GetMapping("/{code}")
    public Mono<Result<StatsVO>> realtime(@PathVariable("code") String code, ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        boolean admin = SaTokenReactorContext.using(exchange,
                () -> StpUtil.hasRole(ShortLinkConstants.ROLE_ADMIN));
        return shortLinkService.assertReadable(code, userId, admin)
                .then(statsQueryService.realtime(code))
                .map(Result::ok);
    }

    @Operation(summary = "历史统计（按日归档）")
    @GetMapping("/{code}/history")
    public Mono<Result<List<StatsHistoryVO>>> history(
            @PathVariable("code") String code,
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) int days,
            ServerWebExchange exchange) {
        long userId = SaTokenReactorContext.using(exchange, StpUtil::getLoginIdAsLong);
        boolean admin = SaTokenReactorContext.using(exchange,
                () -> StpUtil.hasRole(ShortLinkConstants.ROLE_ADMIN));
        LocalDate since = LocalDate.now().minusDays(days - 1L);
        return shortLinkService.assertReadable(code, userId, admin)
                .then(Reactors.call(() -> statsMapper.selectHistory(code, since)))
                .map(rows -> rows.stream()
                        .map(row -> new StatsHistoryVO(row.getStatDate(), row.getPv(), row.getUv()))
                        .toList())
                .map(Result::ok);
    }
}
