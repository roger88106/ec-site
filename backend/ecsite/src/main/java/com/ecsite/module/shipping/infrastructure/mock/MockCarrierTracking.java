package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.domain.Carrier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * mock の物流サービスの追跡（docs/design/modules/shipping.md「mock」）。<br />
 * 追跡を始めても、物流サービスには何も送らない。
 * テストが追跡を始めた送り状番号を確認するときは、テストで共有するソースの、この mock を包む実装が記録する。
 */
@Component
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled")
public class MockCarrierTracking implements CarrierTracking {

    @Override
    public void startTracking(Carrier carrier, String trackingNumber) {
        // 何も送らない
    }
}
