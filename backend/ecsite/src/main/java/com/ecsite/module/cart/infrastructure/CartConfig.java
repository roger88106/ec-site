package com.ecsite.module.cart.infrastructure;

import com.ecsite.module.cart.application.CartCleanupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * カートモジュールの設定（カートの片付け）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CartCleanupProperties.class)
class CartConfig {
}
