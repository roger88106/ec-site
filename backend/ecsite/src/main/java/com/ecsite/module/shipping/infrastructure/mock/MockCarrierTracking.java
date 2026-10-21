package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.domain.Carrier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * mock の物流サービスの追跡（docs/backend/modules/shipping.md「mock」）。<br />
 * 追跡を始めても、物流サービスには何も送らない。テストのコードが、追跡を始めた送り状番号を確認できるように覚える。
 * 覚えるのはメモリで、mock はローカルとテストだけで使い、1 インスタンスで動かす前提のため。
 */
@Component
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled")
public class MockCarrierTracking implements CarrierTracking {

    /**
     * 追跡を始めた送り状番号（始めた順）
     */
    private final List<Started> started = new CopyOnWriteArrayList<>();

    @Override
    public void startTracking(Carrier carrier, String trackingNumber) {
        started.add(new Started(carrier, trackingNumber));
    }

    /**
     * @return 追跡を始めた送り状番号（始めた順）
     */
    public List<Started> started() {
        return List.copyOf(started);
    }

    /**
     * 追跡を始めた送り状番号
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号（ハイフンを除いた数字）
     */
    public record Started(Carrier carrier, String trackingNumber) {
    }
}
