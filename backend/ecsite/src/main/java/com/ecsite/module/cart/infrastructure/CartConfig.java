package com.ecsite.module.cart.infrastructure;

import com.ecsite.module.cart.application.job.CartCleanupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * カートモジュールの設定
 *
 * <p>カートの片付けの設定を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CartCleanupProperties.class)
class CartConfig {
}
