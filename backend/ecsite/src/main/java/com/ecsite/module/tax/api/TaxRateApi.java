package com.ecsite.module.tax.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.tax.api.model.ConsumptionTaxAmount;
import com.ecsite.module.tax.api.model.TaxRateSchedule;
import com.ecsite.module.tax.api.model.TaxableAmount;
import com.ecsite.module.tax.application.TaxCategoryQueryService;
import com.ecsite.module.tax.application.model.view.TaxCategoryView;
import com.ecsite.module.tax.domain.taxcategory.TaxCalculator;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 税モジュールの公開の API
 */
@Component
@RequiredArgsConstructor
public class TaxRateApi {

    /**
     * 税区分クエリサービス
     */
    private final TaxCategoryQueryService queryService;

    /**
     * 税区分ごとの税率の一覧（予定を含む）を返す。
     *
     * @return すべての税区分の税率の一覧（税率は適用開始日時の古い順）
     */
    public List<TaxRateSchedule> listRateSchedules() {
        return queryService.listTaxCategories().stream().map(TaxRateApi::toSchedule).toList();
    }

    /**
     * 指定した時点の率を返す。
     *
     * @param taxCategoryId 税区分 ID
     * @param instant       時点
     * @return 率（%）。税区分がない、またはその時点の税率がなければ空
     */
    public Optional<BigDecimal> rateAt(@NonNull UUID taxCategoryId, @NonNull Instant instant) {
        return queryService.findTaxCategory(taxCategoryId).flatMap(view -> toSchedule(view).rateAt(instant));
    }

    /**
     * 税込価格を計算する。
     *
     * @param priceExclTax 税抜価格
     * @param ratePercent  率（%）
     * @return 税込価格
     */
    public Money priceInclTax(@NonNull Money priceExclTax, @NonNull BigDecimal ratePercent) {
        return TaxCalculator.priceInclTax(priceExclTax, RatePercent.of(ratePercent));
    }

    /**
     * 税率ごとの消費税額を計算する。
     *
     * @param taxables 税率と税込金額の組の一覧
     * @return 税率ごとの消費税額（税率の高い順）
     */
    public List<ConsumptionTaxAmount> consumptionTaxes(@NonNull List<TaxableAmount> taxables) {
        var domainTaxables = taxables.stream()
                .map(taxable -> new TaxCalculator.Taxable(RatePercent.of(taxable.ratePercent()), taxable.amountInclTax()))
                .toList();
        return TaxCalculator.consumptionTaxes(domainTaxables).stream()
                .map(tax -> new ConsumptionTaxAmount(tax.ratePercent().value(), tax.taxableAmount(), tax.taxAmount()))
                .toList();
    }

// ============================== プライベートメソッド ==============================

    /**
     * 税区分の照会結果を、公開する型（税率の一覧）に変換する。
     */
    private static TaxRateSchedule toSchedule(TaxCategoryView view) {
        return new TaxRateSchedule(view.id(), view.rates().stream()
                .map(rate -> new TaxRateSchedule.Rate(rate.ratePercent(), rate.effectiveFrom(), rate.effectiveTo()))
                .toList());
    }
}
