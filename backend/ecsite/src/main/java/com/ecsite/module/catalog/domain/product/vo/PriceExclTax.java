package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * 税抜価格。1 円以上 9,999,999 円以下の整数。全バリアント共通。
 *
 * @param yen 税抜価格（円）
 */
@ValueObject
public record PriceExclTax(long yen) {

    /**
     * 税抜価格の下限（円）
     */
    private static final long MIN_YEN = 1;

    /**
     * 税抜価格の上限（円）。税込価格と、カートの品目の小計の計算が long と DB の bigint の範囲に収まる大きさにする
     */
    private static final long MAX_YEN = 9_999_999;

    /**
     * コンストラクタ。
     *
     * @param yen 税抜価格（円）
     */
    public PriceExclTax {
        if (yen < MIN_YEN || yen > MAX_YEN) {
            throw BusinessException.of(ProductErrorCode.INVALID_PRICE_EXCL_TAX, List.of(MIN_YEN, MAX_YEN));
        }
    }

    /**
     * 税抜価格を作る。
     *
     * @param yen 税抜価格（円）
     * @return 税抜価格
     */
    public static PriceExclTax of(long yen) {
        return new PriceExclTax(yen);
    }

    /**
     * @return 金額
     */
    public Money toMoney() {
        return Money.of(yen);
    }
}
