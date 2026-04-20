package com.dex.business.service;

import com.dex.business.service.blockchain.UniV3PoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段状态服务：统一生成阶段进度与摘要，供 API 和 Dashboard 复用。
 */
@Service
@RequiredArgsConstructor
public class StageStatusService {

    private final PriceService priceService;
    private final LiquidityService liquidityService;
    private final UniV3PoolService uniV3PoolService;

    public Map<String, Object> getProgress() {
        List<Map<String, Object>> stages = stages();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stages", stages);
        payload.put("pricePairsReady", priceService.getSupportedPairs().size());
        payload.put("poolCount", liquidityService.getAllPools().size());
        payload.put("univ3Status", uniV3PoolService.getSummary().getStatus());
        payload.put("generatedAt", System.currentTimeMillis());
        return payload;
    }

    public Map<String, Object> getSummary() {
        List<Map<String, Object>> stages = stages();
        int doneCount = (int) stages.stream()
                .filter(stage -> Boolean.TRUE.equals(stage.get("done")))
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalStages", stages.size());
        summary.put("doneStages", doneCount);
        summary.put("remainingStages", stages.size() - doneCount);
        summary.put("stagePreview", stages.stream().limit(6).toList());
        summary.put("currentFocus", doneCount >= stages.size() ? "进入下一轮扩展能力" : stages.get(doneCount).get("name"));
        return summary;
    }

    private List<Map<String, Object>> stages() {
        return List.of(
                stage("阶段 0", "工程底座", true, List.of("多模块工程", "Actuator Health", "前后端可启动")),
                stage("阶段 1", "单链读链与原始同步", true, List.of("主网连接检查", "最新区块接口", "UniV3 检查点/续传")),
                stage("阶段 2", "DEX 协议索引与标准化", true, List.of("UniV3 事件入库", "标准化事件查询", "同 pool 顺序保持")),
                stage("阶段 3", "派生指标与数据服务", true, List.of("真实价格接口", "价格历史/K线", "TVL 估算与首页总览缓存")),
                stage("阶段 4", "报价与路由引擎", true, List.of("ETH/USDC 真报价", "少量真实池支持", "返回 pool / fee / block 元信息")),
                stage("阶段 5", "实时化与运维能力", true, List.of("SSE 实时价格推送", "延迟告警与运维总览", "手动回放/补数触发")),
                stage("阶段 6", "重量级能力", false, List.of("多链抽象", "多协议池图", "历史回测导出"))
        );
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
