package com.ecsite.module.cart.application.port;

import java.util.UUID;

/**
 * 商品カタログの腐敗防止層の口（バックエンドのアーキテクチャ 第 1 節、docs/03-機能仕様/cart.md）。<br />
 * カートに入れられるかの判定だけを、カートの言葉で返す。
 */
public interface CatalogDirectory {

    /**
     * バリアントを選べるか（C 側に表示される商品の、販売停止でないバリアントか）。
     *
     * @param variantId バリアント ID
     * @return 選べれば true。存在しないバリアントは false
     */
    boolean isSelectable(UUID variantId);
}
