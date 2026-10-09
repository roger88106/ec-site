package com.ecsite.module.catalog.infrastructure;

import com.ecsite.common.model.Money;
import com.ecsite.module.catalog.domain.product.port.TaxDirectory;
import com.ecsite.module.tax.api.TaxRateApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
     * 税区分が、指定した時点で有効な税率を持つかを返す。
     *
     * @param taxCategoryId 税区分 ID
     * @param instant       時点
     * @return 有効な税率を持てば true
     */
    @Override
    public boolean hasRateAt(UUID taxCategoryId, Instant instant) {
        return taxRateApi.rateAt(taxCategoryId, instant).isPresent();
    }

    /**
     * 指定した時点の税込価格を返す。
     *
     * @param taxCategoryId 税区分 ID
     * @param priceExclTax  税抜価格
     * @param instant       時点
     * @return 税込価格。その時点で有効な税率がなければ空
     */
    @Override
    public Optional<Money> priceInclTaxAt(UUID taxCategoryId, Money priceExclTax, Instant instant) {
        return taxRateApi.rateAt(taxCategoryId, instant)
                .map(ratePercent -> taxRateApi.priceInclTax(priceExclTax, ratePercent));
    }
}
