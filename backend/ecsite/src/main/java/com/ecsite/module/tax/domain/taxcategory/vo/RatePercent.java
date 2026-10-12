package com.ecsite.module.tax.domain.taxcategory.vo;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.tax.domain.taxcategory.TaxErrorCode;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 率（%）。0 以上 100 未満、小数第 2 位まで（AC-7）。<br />
 * 値は末尾の 0 を除いた形にそろえる。書き方（{@code 10} と {@code 10.00}）が違っても、同じ値なら等しい。
 *
 * @param value 率（%）
 */
@ValueObject
public record RatePercent(BigDecimal value) {

    /**
     * 上限（この値を含まない）
     */
    private static final BigDecimal UPPER_BOUND = BigDecimal.valueOf(100);

    /**
     * 小数の最大の桁数
     */
    private static final int MAX_SCALE = 2;

    /**
     * コンストラクタ。
     *
     * @param value 率（%）
     */
    public RatePercent(@NonNull BigDecimal value) {
        var normalized = normalize(value);
        // 率は 0 以上 100 未満で、小数の桁が上限以内
        if (normalized.signum() < 0 || normalized.compareTo(UPPER_BOUND) >= 0 || normalized.scale() > MAX_SCALE) {
            throw BusinessException.of(TaxErrorCode.INVALID_RATE_PERCENT, List.of(MAX_SCALE));
        }
        this.value = normalized;
    }

    /**
     * 率を作る。
     *
     * @param value 率（%）
     * @return 率
     */
    public static RatePercent of(BigDecimal value) {
        return new RatePercent(value);
    }

    /**
     * 末尾の 0 を除く。整数は指数表記（{@code 1E+1}）にならないよう、小数の桁数を 0 にする。
     *
     * @param value 値
     * @return そろえた値
     */
    private static BigDecimal normalize(BigDecimal value) {
        var stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
