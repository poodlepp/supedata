package com.dex.api.controller;

import com.dex.business.service.LiquidityService;
import com.dex.business.service.PriceService;
import com.dex.business.service.blockchain.UniV3PoolService;
import com.dex.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段化交付状态控制器
 */
@RestController
@RequestMapping("/api/v1/stages")
@RequiredArgsConstructor
public class StageController {

    private final PriceService priceService;
    private final LiquidityService liquidityService;
    private final UniV3PoolService uniV3PoolService;

    @GetMapping("/progress")
    public ApiResponse<?> progress() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stages", List.of(
                stage("阶段 0", "工程底座", true, List.of("Docker Compose", "Actuator Health", "统一文档与启动脚本")),
                stage("阶段 1", "单链读链与原始同步", true, List.of("主网连接检查", "最新区块接口", "Uniswap V3 检查点/重启续传")),
                stage("阶段 2", "DEX 协议索引与标准化", true, List.of("UniV3 事件入库", "标准化事件查询", "事件与原始日志顺序保持")),
                stage("阶段 3", "派生指标与数据服务", true, List.of("价格接口", "统计概览", "前端看板")),
                stage("阶段 4", "报价与路由引擎", true, List.of("/api/v1/routes/quote", "候选路径解释", "gas/滑点输出")),
                stage("阶段 5", "实时化与运维能力", false, List.of("实时推送", "告警", "补数/回放")),
                stage("阶段 6", "重量级能力", false, List.of("多链抽象", "Reorg-safe 修复", "回测导出"))
        ));
        payload.put("pricePairsReady", List.of("ETH-USDC", "BTC-USDC", "DAI-USDC").stream().filter(pair -> priceService.getLatestPrice(pair).isPresent()).count());
        payload.put("poolCount", liquidityService.getAllPools().size());
        payload.put("univ3Status", uniV3PoolService.getSummary().getStatus());
        payload.put("generatedAt", System.currentTimeMillis());
        return ApiResponse.success(payload);
    }

    private Map<String, Object> stage(String code, String name, boolean done, List<String> deliverables) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("done", done);
        map.put("deliverables", deliverables);
        return map;
    }
}
