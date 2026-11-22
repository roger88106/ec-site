package com.ecsite.module.cart.domain;

import com.ecsite.common.model.id.VariantId;

import java.time.Instant;

/**
 * カートの品目
 *
 * @param variantId バリアント ID
 * @param quantity  数量
 * @param addedAt   入れた日時
 */
public record CartItem(VariantId variantId, int quantity, Instant addedAt) {

    /**
     * 数量を変えた品目を返す。
     *
     * <p>入れた日時（並びの位置）は変えない。
     *
     * @param newQuantity 新しい数量
     * @return 品目
     */
    CartItem withQuantity(int newQuantity) {
        return new CartItem(variantId, newQuantity, addedAt);
    }
}
