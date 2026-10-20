package com.ecsite.module.inventory.infrastructure;

import com.ecsite.module.inventory.application.StockReservationCleanupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 在庫モジュールの設定（引当の片付け）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StockReservationCleanupProperties.class)
class InventoryConfig {
}
