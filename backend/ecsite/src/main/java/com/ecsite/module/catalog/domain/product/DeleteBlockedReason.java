package com.ecsite.module.catalog.domain.product;

/**
 * 商品を削除できない理由（管理画面に返す）。画面はこの値で理由を出し、商品の状態から推し量らない。
 */
public enum DeleteBlockedReason {

    /**
     * C 側に表示されたことがある（注文や閲覧の記録と結び付くので、削除せずに販売終了にする）
     */
    ALREADY_SHOWN
}
