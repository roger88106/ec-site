package com.ecsite.module.cart.application.port;

import java.util.UUID;

/**
 * 在庫の腐敗防止層の port
 *
 * <p>バリアントの販売可能数を返す。
 */
public interface StockDirectory {

    /**
     * バリアントの販売可能数を返す。
     *
     * <p>全ての注文手続きの引当を差し引く（このカートの注文手続きの引当を含む）。
     *
     * @param variantId バリアント ID
     * @return 販売可能数。在庫がなければ 0
     */
    int sellableQuantity(UUID variantId);
}
