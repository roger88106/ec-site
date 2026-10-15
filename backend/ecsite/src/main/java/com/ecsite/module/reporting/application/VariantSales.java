package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;

import java.util.List;
import java.util.UUID;

/**
 * バリアントの売れ行き
 *
 * @param variantId   バリアント ID
 * @param variantCode バリアント品番
 * @param axisValues  軸の名前と値
 * @param quantity    販売数量
 * @param amount      売上額
 */
public record VariantSales(UUID variantId, String variantCode, List<AxisValue> axisValues, long quantity, Money amount) {

    /**
     * 軸の名前と値
     *
     * @param name  軸の名前
     * @param value 値
     */
    public record AxisValue(String name, String value) {
    }
}
