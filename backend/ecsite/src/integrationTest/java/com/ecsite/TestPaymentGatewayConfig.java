package com.ecsite;

import com.ecsite.module.payment.infrastructure.mock.MockPaymentGateway;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 統合テストの決済サービス。<br />
 * 決済の mock を使うときは、mock を包むテスト用の実装（{@link TestPaymentGateway}）を、決済サービスとして使う。
 * テストは、これを注入して、失敗を起こし、呼ばれた操作を確かめる。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPaymentGatewayConfig {

    /**
     * mock を包むテスト用の実装を作る。
     *
     * @param mock 本番の mock
     * @return テスト用の実装
     */
    @Bean
    @Primary
    @ConditionalOnBooleanProperty(name = "ecsite.payment.mock.enabled")
    TestPaymentGateway testPaymentGateway(MockPaymentGateway mock) {
        return new TestPaymentGateway(mock);
    }
}
