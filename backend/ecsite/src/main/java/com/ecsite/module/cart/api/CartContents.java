package com.ecsite.module.cart.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * カートの中身（docs/design/modules/cart.md「他のモジュールへの公開」）。期限切れのカートは品目が空。
 *
 * @param cartId   カート ID
 * @param memberId 会員 ID（ログインせずに作ったカートは null）。持ち主の確認に使う
 * @param version  バージョン（変更のたびに上がる）
 * @param items    品目（入れた順）
 */
public record CartContents(UUID cartId, @Nullable UUID memberId, long version, List<Item> items) {

    /**
     * 品目
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    public record Item(UUID variantId, int quantity) {
    }
}
