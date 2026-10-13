package com.ecsite.module.cart.application.port;

import java.util.UUID;

/**
 * 在庫の腐敗防止層の口（バックエンドのアーキテクチャ 第 1 節、docs/backend/modules/cart.md）。<br />
 * バリアントの販売可能数を返す。
 */
public interface StockDirectory {

    /**
     * バリアントの販売可能数を返す。<br />
     * カートは注文手続き（引当の持ち主）を知らないので、自分の引当も数える。
     *
     * @param variantId バリアント ID
     * @return 販売可能数。在庫がなければ 0
     */
    int sellableQuantity(UUID variantId);
}
