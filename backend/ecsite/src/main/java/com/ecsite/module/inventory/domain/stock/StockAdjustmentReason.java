package com.ecsite.module.inventory.domain.stock;

/**
 * 調整理由（AC-46、107）。注文に由来する理由は、運営者の調整では選べない。
 */
public enum StockAdjustmentReason {

    /**
     * 入荷
     */
    ARRIVAL,

    /**
     * 棚卸差異
     */
    STOCKTAKE,

    /**
     * 破損・紛失
     */
    DAMAGE_OR_LOSS,

    /**
     * その他。メモが必須
     */
    OTHER,

    /**
     * 注文（注文の成立による減算）
     */
    ORDER,

    /**
     * 注文のキャンセル（キャンセルによる戻し）
     */
    ORDER_CANCELLATION,
    ;

    /**
     * 注文に由来する理由か。
     *
     * @return 注文・注文のキャンセルなら true
     */
    public boolean isFromOrder() {
        return this == ORDER || this == ORDER_CANCELLATION;
    }
}
