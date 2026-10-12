package com.ecsite.module.catalog.api;

import com.ecsite.common.model.value.Money;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * C 側の商品一覧の条件（AC-52〜54）。null の項目では絞り込まない。
 *
 * @param categoryId カテゴリ ID（子孫のカテゴリを含む）
 * @param minPrice   税込価格の下限（円、この値を含む）
 * @param maxPrice   税込価格の上限（円、この値を含む）
 * @param sort       並び順。null は新着順
 */
public record StoreProductQuery(@Nullable UUID categoryId, @Nullable Money minPrice, @Nullable Money maxPrice,
                                @Nullable StoreProductSort sort) {
}
