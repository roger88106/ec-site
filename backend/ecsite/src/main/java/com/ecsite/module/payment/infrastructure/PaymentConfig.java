package com.ecsite.module.payment.infrastructure;

import com.ecsite.module.payment.application.PaymentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 決済モジュールの設定
 *
 * <p>決済の設定（支払の期限、与信の有効期間、戻る先など）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentConfig {
}
