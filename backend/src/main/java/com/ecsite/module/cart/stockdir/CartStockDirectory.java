package com.ecsite.module.cart.stockdir;

import com.ecsite.module.inventory.api.StockReservationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 在庫の腐敗防止層（constitution 4.1、docs/modules/cart.md）。店舗の腐敗防止層と Bean 名が重ならないよう、名前に Cart を付ける。<br />
 * 在庫モジュールの API を呼び、バリアントの販売可能数に変換する。
 */
@Component
@RequiredArgsConstructor
public class CartStockDirectory {

    /**
     * 販売可能数と引当の API
     */
    private final StockReservationApi stockReservationApi;

    /**
     * バリアントの販売可能数（spec 002 BR-11）を返す。<br />
     * カートは注文手続き（引当の持ち主）を知らないので、自分の引当も数える。
     *
     * @param variantId バリアント ID
     * @return 販売可能数。在庫がなければ 0
     */
    public int sellableQuantity(UUID variantId) {
        return stockReservationApi.sellableQuantities(List.of(variantId), null).getOrDefault(variantId, 0);
    }
}
