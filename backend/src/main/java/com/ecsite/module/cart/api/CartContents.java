package com.ecsite.module.cart.api;

import java.util.List;
import java.util.UUID;

/**
 * カートの中身（docs/modules/cart.md「他モジュールへの公開」）。期限切れのカートは品目が空。
 *
 * @param cartId  カート ID
 * @param version バージョン（変更のたびに上がる）
 * @param items   品目（入れた順）
 */
public record CartContents(UUID cartId, long version, List<Item> items) {

    /**
     * 品目
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    public record Item(UUID variantId, int quantity) {
    }
}
