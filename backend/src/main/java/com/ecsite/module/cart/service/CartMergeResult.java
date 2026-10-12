package com.ecsite.module.cart.service;

import java.util.List;
import java.util.UUID;

/**
 * カートを移した結果（AC-123）。
 *
 * @param truncatedItems 数量を 10 に切り詰めた品目（切り詰めた後の数量）
 * @param skippedItems   品目の上限（30）を超えたので移さなかった品目（移す元の数量）
 */
public record CartMergeResult(List<Item> truncatedItems, List<Item> skippedItems) {

    /**
     * 何も切り詰めず、移さなかった品目もない
     */
    static final CartMergeResult EMPTY = new CartMergeResult(List.of(), List.of());

    /**
     * コンストラクタ。一覧は変えられない写しにする。
     *
     * @param truncatedItems 数量を切り詰めた品目
     * @param skippedItems   移さなかった品目
     */
    public CartMergeResult {
        truncatedItems = List.copyOf(truncatedItems);
        skippedItems = List.copyOf(skippedItems);
    }

    /**
     * 品目
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    public record Item(UUID variantId, int quantity) {
    }
}
