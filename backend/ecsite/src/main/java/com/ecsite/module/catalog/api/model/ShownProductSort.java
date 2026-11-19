package com.ecsite.module.catalog.api.model;

/**
 * 店舗に表示される商品の一覧の並び順
 *
 * <p>同じ値なら商品 ID の順。
 */
public enum ShownProductSort {

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
