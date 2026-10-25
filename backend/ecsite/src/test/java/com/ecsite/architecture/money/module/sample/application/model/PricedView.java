package com.ecsite.architecture.money.module.sample.application.model;

import com.ecsite.common.model.value.Money;

/**
 * 金額の検査の例：application.model の直下の view に、整数の価格と、Money の価格がある。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param price      整数の価格（規則に合わない）
 * @param totalPrice Money の価格（規則に合う）
 * @param quantity   数量（金額の名前でない）
 */
public record PricedView(long price, Money totalPrice, int quantity) {
}
