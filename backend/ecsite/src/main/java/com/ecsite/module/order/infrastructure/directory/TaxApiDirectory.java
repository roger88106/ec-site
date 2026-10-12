package com.ecsite.module.order.infrastructure.directory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.port.TaxDirectory;
import com.ecsite.module.tax.api.TaxRateApi;
import com.ecsite.module.tax.api.TaxableAmount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 税の腐敗防止層の実装。税モジュールの API（{@link TaxRateApi}）を呼ぶ。
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
    public Optional<BigDecimal> rateAt(UUID taxCategoryId, Instant instant) {
        return taxRateApi.rateAt(taxCategoryId, instant);
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
