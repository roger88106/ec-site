package com.ecsite.module.catalog.application.model.query;

import com.ecsite.module.catalog.domain.product.PublicationStatus;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * B 側の商品一覧の絞り込みの条件。null の項目では絞り込まない。
 *
 * @param code       商品品番またはバリアント品番の前方一致
 * @param name       商品名の部分一致
 * @param status     公開状態
 * @param categoryId カテゴリ ID（子孫のカテゴリを含む）
 */
public record ProductListQuery(@Nullable String code, @Nullable String name, @Nullable PublicationStatus status,
                               @Nullable UUID categoryId) {
}
