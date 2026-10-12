package com.ecsite.module.cart.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * カートの品目
 *
 * @param variantId バリアント ID
 * @param quantity  数量（1〜10）
 * @param addedAt   入れた日時
 */
public record CartItem(UUID variantId, int quantity, Instant addedAt) {

    /**
     * 数量を変えた品目を返す。入れた日時（並びの位置）は変えない。
     *
     * @param newQuantity 新しい数量
     * @return 品目
     */
    CartItem withQuantity(int newQuantity) {
        return new CartItem(variantId, newQuantity, addedAt);
    }
}
