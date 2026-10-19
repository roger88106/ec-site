package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * 税抜価格。1 円以上の整数。全バリアント共通。
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
     * コンストラクタ。
     *
     * @param yen 税抜価格（円）
     */
    public PriceExclTax {
        if (yen < MIN_YEN) {
            throw BusinessException.of(ProductErrorCode.INVALID_PRICE_EXCL_TAX, List.of(MIN_YEN));
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
