package com.dex.api.service;

import com.dex.business.service.OpsService;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 阶段 5 SSE 推送服务：定期向前端广播价格和运维快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsStreamService {

    private final OpsService opsService;
    private final PrometheusMetrics prometheusMetrics;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        prometheusMetrics.recordSseSubscribers(emitters.size());

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(error -> removeEmitter(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("ops-init")
                    .data(opsService.buildRealtimeSnapshot()));
        } catch (IOException e) {
            removeEmitter(emitter);
        }
        return emitter;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 2000)
    public void broadcastSnapshot() {
        if (emitters.isEmpty()) {
            return;
        }
        Object payload = opsService.buildRealtimeSnapshot();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("ops-update").data(payload));
            } catch (IOException e) {
                log.debug("Failed to push ops SSE event", e);
                removeEmitter(emitter);
            }
        }
    }

    private void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
        prometheusMetrics.recordSseSubscribers(emitters.size());
    }
}
