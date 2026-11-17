package com.ecsite.common.model.value;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * 金額（円）
 *
 * <p>通貨は日本円だけなので、円単位の整数で表す。
 *
 * @param yen 金額（円）
 */
@ValueObject
public record Money(long yen) {

    /**
     * 0 円
     */
    public static final Money ZERO = new Money(0);

    /**
     * 金額の下限（円）
     */
    private static final long MIN_YEN = 0;

    /**
     * コンストラクタ。
     *
     * @param yen 金額（円）
     * @throws BusinessException 金額が負（{@code INVALID_MONEY}）
     */
    public Money {
        if (yen < MIN_YEN) {
            throw BusinessException.of(CommonErrorCode.INVALID_MONEY, List.of(MIN_YEN));
        }
    }

    /**
     * 金額を作る。
     *
     * @param yen 金額（円）
     * @return 金額
     * @throws BusinessException 金額が負（{@code INVALID_MONEY}）
     */
    public static Money of(long yen) {
        return new Money(yen);
    }

    /**
     * 加算する。
     *
     * @param other 加える金額
     * @return 合計の金額
     */
    public Money plus(Money other) {
        return new Money(Math.addExact(yen, other.yen));
    }

    /**
     * 数量を掛ける。
     *
     * @param quantity 数量
     * @return 数量を掛けた金額
     * @throws BusinessException 数量が負で、金額が負になる（{@code INVALID_MONEY}）
     */
    public Money times(int quantity) {
        return new Money(Math.multiplyExact(yen, quantity));
    }
}
