package com.ecsite.config.event;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 処理の済んだイベントの片付けの設定（間隔、保存期間）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventCleanupJob.Properties.class)
class EventCleanupConfig {
}
