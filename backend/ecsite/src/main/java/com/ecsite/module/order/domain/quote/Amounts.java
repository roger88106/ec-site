package com.ecsite.module.order.domain.quote;

import com.ecsite.common.model.Money;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * 金額の内訳（BR-7、8）
 *
 * @param itemsTotal  商品の合計（購入できる品目の小計の合計）
 * @param shippingFee 送料（購入できる品目がなければ 0）
 * @param total       総額
 * @param taxes       税率ごとの消費税額（税率の高い順）
 */
@ValueObject
public record Amounts(Money itemsTotal, Money shippingFee, Money total, List<TaxAmount> taxes) {
}
