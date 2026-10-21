package com.ecsite.module.cart.infrastructure.directory;

import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.cart.application.port.StockDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 在庫の腐敗防止層（バックエンドのアーキテクチャ 第 1 節、docs/design/modules/cart.md）。<br />
 * 在庫モジュールの API を呼び、バリアントの販売可能数に変換する。
 */
@Component
@RequiredArgsConstructor
public class StockApiDirectory implements StockDirectory {

    /**
     * 販売可能数と引当の API
     */
    private final StockReservationApi stockReservationApi;

    /**
     * バリアントの販売可能数を返す。<br />
     * カートは注文手続き（引当の持ち主）を知らないので、自分の引当も数える。
     *
     * @param variantId バリアント ID
     * @return 販売可能数。在庫がなければ 0
     */
    @Override
    public int sellableQuantity(UUID variantId) {
        return stockReservationApi.sellableQuantities(List.of(variantId), null).getOrDefault(variantId, 0);
    }
}
