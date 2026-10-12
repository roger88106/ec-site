package com.ecsite.module.storefront.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 店舗モジュールの設定（店舗の情報）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShopProperties.class)
class StorefrontConfig {
}
