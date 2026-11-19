package com.ecsite.module.order.api.model;

import com.ecsite.common.model.value.Money;

import java.util.List;

/**
 * 金額の内訳
 *
 * @param itemsTotal  商品の合計
 * @param shippingFee 送料
 * @param total       総額
 * @param taxes       税率ごとの消費税額（税率の高い順）
 */
public record Amounts(Money itemsTotal, Money shippingFee, Money total, List<TaxAmount> taxes) {
}
