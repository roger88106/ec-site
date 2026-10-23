package com.ecsite;

import com.ecsite.module.shipping.infrastructure.mock.MockCarrierTracking;
import com.ecsite.module.shipping.infrastructure.mock.TestCarrierTracking;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 統合テストの物流サービスの追跡。<br />
 * 物流の mock を使うときは、mock を包むテスト用の実装（{@link TestCarrierTracking}）を、追跡として使う。
 * テストは、これを注入して、追跡を始めた送り状番号を確認する。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestCarrierTrackingConfig {

    /**
     * mock を包むテスト用の実装を作る。
     *
     * @param mock 本番の mock
     * @return テスト用の実装
     */
    @Bean
    @Primary
    @ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled")
    TestCarrierTracking testCarrierTracking(MockCarrierTracking mock) {
        return new TestCarrierTracking(mock);
    }
}
