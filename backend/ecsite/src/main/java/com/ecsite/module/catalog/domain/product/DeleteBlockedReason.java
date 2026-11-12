package com.ecsite.module.catalog.domain.product;

/**
 * 商品を削除できない理由
 */
public enum DeleteBlockedReason {

    /**
     * C 側に表示されたことがある（注文や閲覧の記録と結び付くので、削除せずに販売終了にする）
     */
    ALREADY_SHOWN
}
