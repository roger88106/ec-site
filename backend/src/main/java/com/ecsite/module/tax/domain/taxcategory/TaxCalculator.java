package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.model.Money;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jmolecules.ddd.annotation.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 税込価格の計算（BR-1）。<br />
 * 税抜価格 ×（100 ＋ 率）÷ 100 の、1 円未満を切り捨てる。誤差が出ないよう十進数で計算する。
 * <p>
 * 商品カタログモジュールは、並べ替え・絞り込みのため SQL でも同じ計算をする（docs/modules/catalog.md）。
 * 計算を変えるときは、両方の結果が一致することを確かめる。
 * </p>
 */
@Service
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaxCalculator {

    /**
     * 率（%）を割合にするための 100
     */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 税込価格を計算する。
     *
     * @param priceExclTax 税抜価格
     * @param ratePercent  率
     * @return 税込価格
     */
    public static Money priceInclTax(Money priceExclTax, RatePercent ratePercent) {
        var inclTax = BigDecimal.valueOf(priceExclTax.yen())
                .multiply(HUNDRED.add(ratePercent.value()))
                .divide(HUNDRED, 0, RoundingMode.DOWN);
        return Money.of(inclTax.longValueExact());
    }
}
