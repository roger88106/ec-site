package com.ecsite.module.cart.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * カートの片付けの設定（バックエンドのアーキテクチャの「水平スケール」）。環境変数で上書きする（例：ECSITE_CART_CLEANUP_RETENTION）。
 *
 * @param interval  片付けのジョブの間隔
 * @param retention 保存期間（カートの期限から、消すまでの時間。既定は 0 で、期限で消す）
 * @param batchSize 1 回の削除で消す件数の上限
 */
@Validated
@ConfigurationProperties("ecsite.cart.cleanup")
public record CartCleanupProperties(
        @DefaultValue("1h") @NotNull Duration interval,
        @DefaultValue("0d") @NotNull Duration retention,
        @DefaultValue("1000") @Min(1) int batchSize) {
}
