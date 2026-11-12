package com.ecsite.module.reporting.infrastructure;

import com.ecsite.module.reporting.application.ReportingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * レポートモジュールの設定
 *
 * <p>レポートの設定の値（{@link ReportingProperties}）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportingProperties.class)
class ReportingConfig {
}
