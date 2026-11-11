package com.ecsite.module.storefront.infrastructure;

import com.ecsite.module.storefront.application.ShopProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 店舗モジュールの設定
 *
 * <p>店舗の情報の設定を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShopProperties.class)
class StorefrontConfig {
}
