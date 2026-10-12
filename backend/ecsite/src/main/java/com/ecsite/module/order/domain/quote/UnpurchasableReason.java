package com.ecsite.module.order.domain.quote;

/**
 * 購入できない理由
 */
public enum UnpurchasableReason {

    /**
     * 商品が C 側に表示されない。有効な税率がない場合も含む
     */
    NOT_SHOWN,

    /**
     * バリアントが販売停止
     */
    SALES_SUSPENDED,

    /**
     * 販売可能数がない
     */
    OUT_OF_STOCK,

    /**
     * 販売可能数が数量より少ない
     */
    INSUFFICIENT_STOCK,
    ;

    /**
     * 在庫による理由か（在庫不足は時間がたてば解消しうるので、注文の拒否を 409 にする）。
     *
     * @return 販売可能数がない・足りないなら true
     */
    public boolean isStockShortage() {
        return this == OUT_OF_STOCK || this == INSUFFICIENT_STOCK;
    }
}
