package com.ecsite.common.presentation;

import com.ecsite.api.store.model.Price;
import com.ecsite.common.model.value.Money;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 店舗の API の価格（税込と税抜の組）を作る。商品の表示と注文の表示で同じ形にする。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorePrices {

    /**
     * 店舗の API の価格を作る。
     *
     * @param inclTax 税込の金額
     * @param exclTax 税抜の金額
     * @return 店舗の API の価格
     */
    public static Price of(Money inclTax, Money exclTax) {
        return new Price().inclTax(inclTax.yen()).exclTax(exclTax.yen());
    }
}
