package com.ecsite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定期ジョブの起動の設定（バックエンドのアーキテクチャの「水平スケール」）。<br />
 * {@code ecsite.scheduling.enabled} が false なら、定期ジョブを自動では動かさない（統合テストは、テストからジョブを呼ぶ）。
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "ecsite.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig {
}
