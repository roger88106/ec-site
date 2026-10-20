package com.ecsite.module.inventory.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 引当の片付けの設定（バックエンドのアーキテクチャ 第 5 節）。環境変数で上書きする（例：ECSITE_INVENTORY_CLEANUP_RETENTION）。
 *
 * @param interval  片付けのジョブの間隔
 * @param retention 保存期間（引当の期限から、消すまでの時間）
 * @param batchSize 1 回の削除で消す件数の上限
 */
@Validated
@ConfigurationProperties("ecsite.inventory.cleanup")
public record StockReservationCleanupProperties(
        @DefaultValue("1h") @NotNull Duration interval,
        @DefaultValue("1d") @NotNull Duration retention,
        @DefaultValue("1000") @Min(1) int batchSize) {
}
