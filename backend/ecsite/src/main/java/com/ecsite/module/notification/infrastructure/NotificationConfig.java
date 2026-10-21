package com.ecsite.module.notification.infrastructure;

import com.ecsite.module.notification.application.NotificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通知モジュールの設定（送信元のアドレス）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
class NotificationConfig {
}
