package com.ecsite.module.storefront.application.port;

import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 商品一覧の絞り込みと並び順（AC-52〜54）
 *
 * @param categoryId カテゴリ ID（子孫のカテゴリを含む）。null は絞り込まない
 * @param minPrice   税込価格の下限（円、この値を含む）。null は絞り込まない
 * @param maxPrice   税込価格の上限（円、この値を含む）。null は絞り込まない
 * @param sort       並び順
 */
public record ProductCondition(@Nullable UUID categoryId, @Nullable Long minPrice, @Nullable Long maxPrice, Sort sort) {

    /**
     * 並び順
     */
    public enum Sort {

        /**
         * 新着順
         */
        NEWEST,

        /**
         * 価格の安い順（税込）
         */
        PRICE_ASC,

        /**
         * 価格の高い順（税込）
         */
        PRICE_DESC,
    }
}
