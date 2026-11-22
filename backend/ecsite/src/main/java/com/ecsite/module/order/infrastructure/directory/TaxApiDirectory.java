package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import com.ecsite.module.tax.api.TaxRateApi;
import com.ecsite.module.tax.api.model.TaxableAmount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 税の腐敗防止層
 *
 * <p>税モジュールの API を呼ぶ。
 */
@Component
@RequiredArgsConstructor
class TaxApiDirectory implements TaxDirectory {

    /**
     * 税モジュールの API
     */
    private final TaxRateApi taxRateApi;

    /**
     * 指定した時点の税率を返す。
     */
    @Override
    public Optional<BigDecimal> rateAt(TaxCategoryId taxCategoryId, Instant instant) {
        return taxRateApi.rateAt(taxCategoryId, instant);
    }

    /**
     * 指定した時点の税率を、税区分ごとにまとめて返す。
     */
    @Override
    public Map<TaxCategoryId, BigDecimal> ratesAt(Collection<TaxCategoryId> taxCategoryIds, Instant instant) {
        return taxRateApi.ratesAt(taxCategoryIds, instant);
    }

    /**
     * 税抜価格から税込価格を求める。
     */
    @Override
    public Money priceInclTax(Money priceExclTax, BigDecimal ratePercent) {
        return taxRateApi.priceInclTax(priceExclTax, ratePercent);
    }

    /**
     * 税率ごとの消費税額を求める。
     */
    @Override
    public List<TaxAmount> consumptionTaxes(List<Taxable> taxables) {
        var amounts = taxables.stream()
                .map(taxable -> new TaxableAmount(taxable.ratePercent(), taxable.amountInclTax()))
                .toList();
        return taxRateApi.consumptionTaxes(amounts).stream()
                .map(tax -> new TaxAmount(tax.ratePercent(), tax.taxableAmount(), tax.taxAmount()))
                .toList();
    }
}
