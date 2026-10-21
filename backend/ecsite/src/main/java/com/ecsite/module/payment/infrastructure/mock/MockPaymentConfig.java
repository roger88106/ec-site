package com.ecsite.module.payment.infrastructure.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * mock の決済サービスの設定を有効にする。決済の mock フラグを立てたとき（{@link MockPaymentGateway} と同じ条件）だけ。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "ecsite.payment.mock.enabled")
@EnableConfigurationProperties(MockPaymentProperties.class)
class MockPaymentConfig {
}
