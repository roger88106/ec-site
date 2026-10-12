package com.ecsite.module.tax.domain.taxcategory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jmolecules.ddd.annotation.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 税込価格と、税率ごとの消費税額の計算。誤差が出ないよう十進数で計算する。<br />
 * 税込価格は、税抜価格 ×（100 ＋ 率）÷ 100 の、1 円未満を切り捨てる。
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

    /**
     * 税率ごとの消費税額を計算する。<br />
     * 税率ごとに税込金額を合計し、合計 × 率 ÷（100 ＋ 率）の 1 円未満を 1 回だけ切り捨てる。
     * 品目ごとには端数処理しない（適格請求書の端数処理のルール）。
     *
     * @param taxables 税率と税込金額の組の一覧（品目の小計、送料など）
     * @return 税率ごとの消費税額（税率の高い順）
     */
    public static List<ConsumptionTax> consumptionTaxes(List<Taxable> taxables) {
        // 率は末尾の 0 をそろえてあるので、同じ率は同じキーになる
        var totals = new TreeMap<RatePercent, Money>(Comparator.comparing(RatePercent::value).reversed());
        taxables.forEach(taxable -> totals.merge(taxable.ratePercent(), taxable.amountInclTax(), Money::plus));
        return totals.entrySet().stream()
                .map(TaxCalculator::toConsumptionTax)
                .toList();
    }

    /**
     * 1 つの税率の合計から消費税額を求める。
     */
    private static ConsumptionTax toConsumptionTax(Map.Entry<RatePercent, Money> total) {
        var rate = total.getKey().value();
        var tax = BigDecimal.valueOf(total.getValue().yen())
                .multiply(rate)
                .divide(HUNDRED.add(rate), 0, RoundingMode.DOWN);
        return new ConsumptionTax(total.getKey(), total.getValue(), Money.of(tax.longValueExact()));
    }

    /**
     * 消費税額の計算の対象
     *
     * @param ratePercent   率
     * @param amountInclTax 税込金額
     */
    public record Taxable(RatePercent ratePercent, Money amountInclTax) {
    }

    /**
     * 1 つの税率の消費税額
     *
     * @param ratePercent   率
     * @param taxableAmount その率の対象の税込金額の合計
     * @param taxAmount     消費税額
     */
    public record ConsumptionTax(RatePercent ratePercent, Money taxableAmount, Money taxAmount) {
    }
}
