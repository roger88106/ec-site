package com.ecsite.common.model.value;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * 金額（円）。<br />
 * 通貨は日本円だけなので、円単位の整数で表す（constitution 1、7.3）。小数を含む計算（税込価格など）は
 * 呼び出す側が誤差の出ない十進数で行い、端数を処理してから金額にする。
 *
 * @param yen 金額（円）。0 以上
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
     */
    public Money {
        if (yen < MIN_YEN) {
            throw BusinessException.of(CommonErrorCode.INVALID_MONEY, List.of(MIN_YEN));
        }
    }

    /**
     * 金額を作る。
     *
     * @param yen 金額（円）。0 以上
     * @return 金額
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
     * 数量を掛ける。<br />
     * 桁あふれは {@link ArithmeticException}、負の数量は負の金額として {@link BusinessException} になる。
     *
     * @param quantity 数量
     * @return 数量を掛けた金額
     */
    public Money times(int quantity) {
        return new Money(Math.multiplyExact(yen, quantity));
    }
}
