package com.ecsite.module.payment.infrastructure.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * mock の決済サービスの設定を有効にする。mock を使うとき（{@link MockPaymentGateway} と同じ条件）だけ。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ecsite.payment.gateway", havingValue = "mock", matchIfMissing = true)
@EnableConfigurationProperties(MockPaymentProperties.class)
class MockPaymentConfig {
}
