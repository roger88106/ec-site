package com.ecsite.module.order.domain.quote.port;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.TaxAmount;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 税の腐敗防止層（バックエンドのアーキテクチャの「モジュラーモノリス」）。税込単価と消費税額の計算は、税モジュールに任せる。
 */
public interface TaxDirectory {

    /**
     * 指定した時点の率を返す。
     *
     * @param taxCategoryId 税区分 ID
     * @param instant       時点
     * @return 率（%）。税区分がない、またはその時点の税率がなければ空
     */
    Optional<BigDecimal> rateAt(UUID taxCategoryId, Instant instant);

    /**
     * 税込価格を計算する。
     *
     * @param priceExclTax 税抜価格
     * @param ratePercent  率（%）
     * @return 税込価格
     */
    Money priceInclTax(Money priceExclTax, BigDecimal ratePercent);

    /**
     * 税率ごとの消費税額を計算する。
     *
     * @param taxables 税率と税込金額の組の一覧
     * @return 税率ごとの消費税額（税率の高い順）
     */
    List<TaxAmount> consumptionTaxes(List<Taxable> taxables);

    /**
     * 消費税額の計算の対象
     *
     * @param ratePercent   率（%）
     * @param amountInclTax 税込金額
     */
    record Taxable(BigDecimal ratePercent, Money amountInclTax) {
    }
}
