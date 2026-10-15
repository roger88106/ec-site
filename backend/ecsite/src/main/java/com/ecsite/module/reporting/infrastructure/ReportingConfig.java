package com.ecsite.module.reporting.infrastructure;

import com.ecsite.module.reporting.application.ReportingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * レポートモジュールの設定（会計年度の始まりの月）を有効にする。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportingProperties.class)
class ReportingConfig {
}
