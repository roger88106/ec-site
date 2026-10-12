package com.ecsite.module.catalog.api;

/**
 * C 側の商品一覧の並び順（AC-54）。同じ値なら商品 ID の順
 */
public enum StoreProductSort {

    /**
     * 新着順（初回表示日時の新しい順）
     */
    NEWEST,

    /**
     * 価格の安い順（税込価格）
     */
    PRICE_ASC,

    /**
     * 価格の高い順（税込価格）
     */
    PRICE_DESC,
}
