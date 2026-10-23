package com.ecsite.config.event;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 未完了のイベントの再発行の設定（間隔、再発行するイベントの発行からの時間の下限）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventResubmissionJob.Properties.class)
class EventResubmissionConfig {
}
