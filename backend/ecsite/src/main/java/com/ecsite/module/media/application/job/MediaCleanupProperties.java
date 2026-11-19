package com.ecsite.module.media.application.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 仮のアセットの片付けの設定
 *
 * <p>環境変数で上書きする（例：ECSITE_MEDIA_CLEANUP_RETENTION）。
 *
 * <p>保存期間は、他の片付けと違って 0 を許さない。0 にすると、用意から登録までの間の仮のアセットを消しうるため。
 *
 * <p>1 回の実行で消す件数には、最大を置く。消した件数だけ、外部サービスを呼ぶリスナーのキューにイベントが入り、
 * キューの上限を超えると、同じキューを使う他のリスナーのイベントも拒否されるため（イベントの再発行の件数の上限と同じ考え方）。
 *
 * @param interval  片付けのジョブの間隔
 * @param retention 保存期間（用意した時刻から、消すまでの時間）
 * @param batchSize 1 回の実行で消す件数の上限
 */
@Validated
@ConfigurationProperties("ecsite.media.cleanup")
public record MediaCleanupProperties(
        @DefaultValue("1h") @NotNull @DurationMin(millis = 1) Duration interval,
        @DefaultValue("1d") @NotNull @DurationMin(minutes = 1) Duration retention,
        @DefaultValue("500") @Min(1) @Max(500) int batchSize) {
}
