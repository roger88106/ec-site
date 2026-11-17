package com.ecsite.config.event;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * イベントの記録の片付けと再発行の設定
 *
 * <p>片付けと再発行のジョブの設定の型を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EventCleanupJob.Properties.class, EventResubmissionJob.Properties.class})
class EventConfig {
}
