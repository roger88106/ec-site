package com.ecsite.module.payment.infrastructure;

import com.ecsite.module.payment.application.PaymentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 決済モジュールの設定（決済サービス、支払の期限、戻る先）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentConfig {
}
