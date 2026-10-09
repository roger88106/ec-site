package com.ecsite.module.inventory.domain.stock;

/**
 * 調整理由（AC-46）
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
}
