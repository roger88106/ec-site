package com.ecsite.module.catalog.domain.product;

/**
 * 公開状態（AC-32）
 */
public enum PublicationStatus {

    /**
     * 非公開
     */
    UNPUBLISHED,

    /**
     * 公開
     */
    PUBLISHED,

    /**
     * 販売終了。公開にも非公開にも戻せない
     */
    DISCONTINUED,
}
