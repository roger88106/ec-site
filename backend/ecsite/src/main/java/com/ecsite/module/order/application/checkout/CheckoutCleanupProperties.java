package com.ecsite.module.order.application.checkout;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 注文手続きの片付けの設定（バックエンドのアーキテクチャ 第 5 節）。環境変数で上書きする（例：ECSITE_ORDER_CLEANUP_RETENTION）。
 *
 * @param interval  片付けのジョブの間隔
 * @param retention 保存期間（注文手続きの期限から、消すまでの時間）
 * @param batchSize 1 回の削除で消す件数の上限
 */
@Validated
@ConfigurationProperties("ecsite.order.cleanup")
public record CheckoutCleanupProperties(
        @DefaultValue("1h") @NotNull Duration interval,
        @DefaultValue("1d") @NotNull Duration retention,
        @DefaultValue("1000") @Min(1) int batchSize) {
}
