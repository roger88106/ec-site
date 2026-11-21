package com.ecsite.module.catalog.infrastructure.directory;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.application.port.TaxDirectory;
import com.ecsite.module.tax.api.TaxRateApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 税の腐敗防止層
 *
 * <p>税モジュールの API を呼ぶ。取得の経路が 2 つある。
 *
 * <ul>
 *   <li>{@link #ratesAt}（店舗の一覧と詳細の表示）：{@link TaxRateScheduleCache}（有効期間 30 秒）から読む。表示のための多数の読み取りで、
 *   税率の登録・変更の反映が最大 30 秒遅れるだけだから。</li>
 *   <li>{@link #hasRateAt}・{@link #priceInclTaxAt}（管理画面の詳細の税込価格と、商品の登録の確認）：税モジュールに毎回問い合わせる。</li>
 * </ul>
 *
 * <p>注文の見積もり（order のモジュールの {@code TaxApiDirectory}）は、請求する金額を決めるので毎回問い合わせる。そのため、税率を変えた直後の最大 30 秒は、
 * 店舗の表示の税込価格と注文の見積もりがずれうる。注文手続きの確認の画面は注文の見積もりを示すので、利用者は請求する総額を確認してから注文する。
 */
@Component
@RequiredArgsConstructor
class TaxApiDirectory implements TaxDirectory {

    /**
     * 税モジュールの API
     */
    private final TaxRateApi taxRateApi;

    /**
     * 税率の一覧のキャッシュ
     */
    private final TaxRateScheduleCache taxRateScheduleCache;

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

    /**
     * 全ての税区分について、指定した時点で有効な率を返す。
     *
     * @param instant 時点
     * @return 税区分 ID → 率（%）
     */
    @Override
    public Map<UUID, BigDecimal> ratesAt(Instant instant) {
        var rates = new HashMap<UUID, BigDecimal>();
        for (var schedule : taxRateScheduleCache.schedules()) {
            schedule.rateAt(instant).ifPresent(rate -> rates.put(schedule.taxCategoryId(), rate));
        }
        return rates;
    }
}
