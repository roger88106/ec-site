package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.module.payment.infrastructure.ConditionalOnPaymentMock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * mock の決済サービスの設定
 *
 * <p>mock の決済サービスの設定の値を有効にする。決済の mock フラグを立てたときだけ有効にする。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnPaymentMock
@EnableConfigurationProperties(MockPaymentProperties.class)
class MockPaymentConfig {
}
