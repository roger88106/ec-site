package com.ecsite.module.catalog.application.model.command;

import com.ecsite.common.model.id.ProductId;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 公開期間の変更コマンド
 *
 * @param productId    商品 ID
 * @param publishStart 公開開始日時。null は開始を限らない
 * @param publishEnd   公開終了日時。null は無期限
 * @param version      取得時の商品のバージョン
 */
public record ChangePublicationPeriodCommand(@NonNull ProductId productId, @Nullable Instant publishStart,
                                             @Nullable Instant publishEnd, long version) {
}
