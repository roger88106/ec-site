package com.ecsite.module.tax.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.tax.api.model.ConsumptionTaxAmount;
import com.ecsite.module.tax.api.model.TaxRateSchedule;
import com.ecsite.module.tax.api.model.TaxableAmount;
import com.ecsite.module.tax.application.TaxCategoryCommandService;
import com.ecsite.module.tax.application.model.command.CreateTaxCategoryCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand.Rate;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品カタログ・注文向けの税 API の統合テスト
 *
 * <p>現在時刻は 2026-10-08 00:00（日本時間）に固定する。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class TaxRateApiTest {

    // ============================== 定数 ==============================

    /**
     * 2027 年 4 月の初め
     *
     * <p>日本時間の 2027-04-01 00:00。
     */
    private static final Instant APR_2027 = Instant.parse("2027-03-31T15:00:00Z");

    /**
     * 2027-04-01 より前の税率（%）
     */
    private static final BigDecimal BEFORE_RATE = new BigDecimal("10");

    /**
     * 2027-04-01 からの税率（%）
     */
    private static final BigDecimal AFTER_RATE = new BigDecimal("8");

    // ============================== Bean ==============================

    @Autowired
    TaxRateApi taxRateApi;

    @Autowired
    TaxCategoryCommandService commandService;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("税率の一覧")
    class RateSchedules {

        @Test
        @DisplayName("税区分ごとの税率の一覧を、予定の税率を含めて返す")
        void listsRateSchedulesIncludingFuture() {
            // 準備：2027-04-01 に税率が変わる税区分
            var id = createSwitchingCategory();

            // 操作：税率の一覧から、この税区分の分を選ぶ
            var schedule = scheduleOf(id);

            // 確認：今の税率と、予定の税率が、適用期間の古い順に並ぶ
            assertThat(schedule.rates()).extracting(TaxRateSchedule.Rate::ratePercent)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(BEFORE_RATE, AFTER_RATE);
        }

        @Test
        @DisplayName("返した税率の一覧から、指定した時点の率を引ける")
        void scheduleReturnsRateAt() {
            // 準備：2027-04-01 に税率が変わる税区分
            var id = createSwitchingCategory();

            // 操作：税率の一覧から、この税区分の分を選ぶ
            var schedule = scheduleOf(id);

            // 確認：切り替えの直前は前の率、切り替えの時点からは後の率
            assertThat(schedule.rateAt(APR_2027.minusSeconds(1))).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo(BEFORE_RATE));
            assertThat(schedule.rateAt(APR_2027)).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo(AFTER_RATE));
        }
    }

    @Nested
    @DisplayName("指定した時点の率")
    class RateAt {

        @Test
        @DisplayName("AC-4 指定した時点の率を返す")
        void returnsRateAt() {
            // 準備：2027-04-01 に税率が変わる税区分
            var id = createSwitchingCategory();

            // 操作と確認：切り替えの直前は前の率、切り替えの時点からは後の率
            assertThat(taxRateApi.rateAt(id, APR_2027.minusSeconds(1))).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo(BEFORE_RATE));
            assertThat(taxRateApi.rateAt(id, APR_2027)).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo(AFTER_RATE));
        }

        @Test
        @DisplayName("その時点の税率がない税区分は、率が空になる")
        void returnsEmptyWhenNoRate() {
            // 準備：2027-04-01 から始まる税率だけを持つ税区分
            var id = createWithRates(new Rate(null, BEFORE_RATE, APR_2027, null));

            // 操作と確認：開始より前の率は空
            assertThat(taxRateApi.rateAt(id, APR_2027.minusSeconds(1))).isEmpty();
        }

        @Test
        @DisplayName("存在しない税区分は、率が空になる")
        void returnsEmptyForUnknownCategory() {
            // 操作と確認：登録していない ID の率は空
            assertThat(taxRateApi.rateAt(UUID.randomUUID(), APR_2027)).isEmpty();
        }
    }

    @Nested
    @DisplayName("複数の税区分の指定した時点の率")
    class RatesAt {

        @Test
        @DisplayName("1 件の税区分の、指定した時点の率を返す")
        void returnsRateOfSingleCategory() {
            // 準備：2027-04-01 に税率が変わる税区分
            var id = createSwitchingCategory();

            // 操作：切り替えの時点の率をまとめて求める
            var rates = taxRateApi.ratesAt(List.of(id), APR_2027);

            // 確認：後の率だけが入る
            assertThat(rates).containsOnlyKeys(id);
            assertThat(rates.get(id)).isEqualByComparingTo(AFTER_RATE);
        }

        @Test
        @DisplayName("複数の税区分の、指定した時点の率を、税区分ごとに返す")
        void returnsRatesOfMultipleCategories() {
            // 準備：2027-04-01 に税率が変わる税区分と、率が変わらない税区分
            var switching = createSwitchingCategory();
            var fixed = createWithRates(new Rate(null, new BigDecimal("0"), null, null));

            // 操作：切り替えの直前の率をまとめて求める
            var rates = taxRateApi.ratesAt(List.of(switching, fixed), APR_2027.minusSeconds(1));

            // 確認：税区分ごとに、その時点の率が入る
            assertThat(rates).containsOnlyKeys(switching, fixed);
            assertThat(rates.get(switching)).isEqualByComparingTo(BEFORE_RATE);
            assertThat(rates.get(fixed)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("1 件ずつ求めた率と一致する")
        void matchesRateAtOfEachCategory() {
            // 準備：2027-04-01 に税率が変わる税区分と、率が変わらない税区分
            var switching = createSwitchingCategory();
            var fixed = createWithRates(new Rate(null, AFTER_RATE, null, null));

            // 操作：切り替えの時点の率を、まとめて求めた率と 1 件ずつ求めた率を比べる
            var rates = taxRateApi.ratesAt(List.of(switching, fixed), APR_2027);

            // 確認：税区分ごとに、1 件の率と同じ
            assertThat(rates.get(switching)).isEqualByComparingTo(taxRateApi.rateAt(switching, APR_2027).orElseThrow());
            assertThat(rates.get(fixed)).isEqualByComparingTo(taxRateApi.rateAt(fixed, APR_2027).orElseThrow());
        }

        @Test
        @DisplayName("その時点の税率がない税区分は、結果に含まれない")
        void excludesCategoryWithoutRateAtInstant() {
            // 準備：2027-04-01 から始まる税率だけを持つ税区分と、率のある税区分
            var noRate = createWithRates(new Rate(null, BEFORE_RATE, APR_2027, null));
            var withRate = createSwitchingCategory();

            // 操作：開始より前の率をまとめて求める
            var rates = taxRateApi.ratesAt(List.of(noRate, withRate), APR_2027.minusSeconds(1));

            // 確認：率のある税区分だけが入る
            assertThat(rates).containsOnlyKeys(withRate);
        }

        @Test
        @DisplayName("存在しない税区分は、結果に含まれない")
        void excludesUnknownCategory() {
            // 準備：登録した税区分と、登録していない ID
            var id = createSwitchingCategory();

            // 操作：両方の率をまとめて求める
            var rates = taxRateApi.ratesAt(List.of(id, UUID.randomUUID()), APR_2027);

            // 確認：登録した税区分だけが入る
            assertThat(rates).containsOnlyKeys(id);
        }

        @Test
        @DisplayName("空のコレクションなら、空を返す")
        void returnsEmptyForEmptyIds() {
            // 操作と確認：税区分 ID がなければ空
            assertThat(taxRateApi.ratesAt(List.of(), APR_2027)).isEmpty();
        }
    }

    @Nested
    @DisplayName("税額の計算")
    class Calculation {

        @Test
        @DisplayName("BR-1 税込価格を計算する")
        void calculatesPriceInclTax() {
            // 操作と確認：1,234 円の 8% の税込価格は 1,332 円
            assertThat(taxRateApi.priceInclTax(Money.of(1_234), new BigDecimal("8"))).isEqualTo(Money.of(1_332));
        }

        @Test
        @DisplayName("BR-8 税率ごとの消費税額を、税率の高い順に返す")
        void calculatesConsumptionTaxes() {
            // 準備：8% の 1,332 円、10% の 2,970 円、書き方の違う 10.00% の 1,000 円
            var taxables = List.of(
                    new TaxableAmount(new BigDecimal("8"), Money.of(1_332)),
                    new TaxableAmount(new BigDecimal("10"), Money.of(2_970)),
                    new TaxableAmount(new BigDecimal("10.00"), Money.of(1_000)));

            // 操作：消費税額を計算する
            var taxes = taxRateApi.consumptionTaxes(taxables);

            // 確認：10% は 3,970 円から 360 円、8% は 1,332 円から 98 円の順
            assertThat(taxes).containsExactly(
                    new ConsumptionTaxAmount(new BigDecimal("10"), Money.of(3_970), Money.of(360)),
                    new ConsumptionTaxAmount(new BigDecimal("8"), Money.of(1_332), Money.of(98)));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 2027-04-01 に税率が変わる税区分を登録し、その ID を返す。
     *
     * <p>2027-04-01 より前は {@link #BEFORE_RATE}、2027-04-01 からは {@link #AFTER_RATE}。
     *
     * @return 税区分 ID
     */
    private UUID createSwitchingCategory() {
        return createWithRates(new Rate(null, BEFORE_RATE, null, APR_2027), new Rate(null, AFTER_RATE, APR_2027, null));
    }

    /**
     * 税区分を登録し、税率の一覧を設定して、その ID を返す。
     *
     * <p>他のテストの税区分と名称が重ならないよう、名称に実行ごとの値を付ける。
     *
     * @param rates 税率の一覧
     * @return 税区分 ID
     */
    private UUID createWithRates(Rate... rates) {
        var id = commandService.createTaxCategory(new CreateTaxCategoryCommand("標準 " + UUID.randomUUID().toString().substring(0, 8)));
        commandService.replaceTaxRates(new ReplaceTaxRatesCommand(id, 0, List.of(rates)));
        return id;
    }

    /**
     * 税率の一覧から、指定した税区分の分を選ぶ。
     *
     * <p>一覧には他のテストの税区分も含まれるので、ID で絞る。
     *
     * @param id 税区分 ID
     * @return 税区分の税率の一覧
     */
    private TaxRateSchedule scheduleOf(UUID id) {
        return taxRateApi.listRateSchedules().stream()
                .filter(s -> s.taxCategoryId().equals(id))
                .findFirst().orElseThrow();
    }
}
