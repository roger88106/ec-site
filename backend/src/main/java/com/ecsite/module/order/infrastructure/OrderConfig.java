package com.ecsite.module.order.infrastructure;

import com.ecsite.module.order.application.OrderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注文モジュールの設定（送料、引当の期限）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderProperties.class)
class OrderConfig {
}
