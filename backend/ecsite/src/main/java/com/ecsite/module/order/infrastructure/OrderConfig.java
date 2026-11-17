package com.ecsite.module.order.infrastructure;

import com.ecsite.module.order.application.OrderProperties;
import com.ecsite.module.order.application.job.CheckoutCleanupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注文モジュールの設定
 *
 * <p>注文の設定と、注文手続きの片付けの設定を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OrderProperties.class, CheckoutCleanupProperties.class})
class OrderConfig {
}
