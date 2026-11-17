package com.ecsite.module.reporting.application.model;

import com.ecsite.common.model.value.Money;

import java.util.UUID;

/**
 * バリアントの売上の集計
 *
 * @param variantId   バリアント ID
 * @param variantCode バリアント品番の写し
 * @param quantity    販売数量
 * @param amount      売上額
 */
public record VariantTotal(UUID variantId, String variantCode, long quantity, Money amount) {
}
