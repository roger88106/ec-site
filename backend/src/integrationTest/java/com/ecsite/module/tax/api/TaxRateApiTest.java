package com.ecsite.module.tax.api;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.Money;
import com.ecsite.module.tax.application.TaxCategoryCommandService;
import com.ecsite.module.tax.application.model.command.CreateTaxCategoryCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand;
import com.ecsite.module.tax.application.model.command.ReplaceTaxRatesCommand.Rate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品カタログ向けの税 API の統合テスト（T112）。<br />
 * 現在時刻は 2026-10-08 00:00（日本時間）に固定する。テストごとにロールバックする。
 */
@IntegrationTest
@Import(TestClockConfig.class)
@Transactional
class TaxRateApiTest {

    /**
     * 2027-04-01 00:00（日本時間）
     */
    private static final Instant APR_2027 = Instant.parse("2027-03-31T15:00:00Z");

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    TaxRateApi taxRateApi;

    @Autowired
    TaxCategoryCommandService commandService;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setClock() {
        // 同じ構成の他のテストが時計を動かしていても、現在時刻を決めてから始める
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("税区分ごとの税率の一覧を、予定の税率を含めて返す")
    void listsRateSchedulesIncludingFuture() {
        var id = createWithRates(new Rate(null, new BigDecimal("10"), null, APR_2027),
                new Rate(null, new BigDecimal("8"), APR_2027, null));

        var schedule = taxRateApi.listRateSchedules().stream()
                .filter(s -> s.taxCategoryId().equals(id))
                .findFirst().orElseThrow();

        assertThat(schedule.rates()).extracting(TaxRateSchedule.Rate::ratePercent)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10"), new BigDecimal("8"));
        assertThat(schedule.rateAt(APR_2027.minusSeconds(1))).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("10"));
        assertThat(schedule.rateAt(APR_2027)).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("8"));
    }

    @Test
    @DisplayName("AC-4 指定した時点の率を返す")
    void returnsRateAt() {
        var id = createWithRates(new Rate(null, new BigDecimal("10"), null, APR_2027),
                new Rate(null, new BigDecimal("8"), APR_2027, null));

        assertThat(taxRateApi.rateAt(id, APR_2027.minusSeconds(1))).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("10"));
        assertThat(taxRateApi.rateAt(id, APR_2027)).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("8"));
    }

    @Test
    @DisplayName("AC-6 その時点の税率がない税区分は、率が空になる")
    void returnsEmptyWhenNoRate() {
        var id = createWithRates(new Rate(null, new BigDecimal("10"), APR_2027, null));

        assertThat(taxRateApi.rateAt(id, APR_2027.minusSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("存在しない税区分は、率が空になる")
    void returnsEmptyForUnknownCategory() {
        assertThat(taxRateApi.rateAt(UUID.randomUUID(), APR_2027)).isEmpty();
    }

    @Test
    @DisplayName("BR-1 税込価格を計算する")
    void calculatesPriceInclTax() {
        assertThat(taxRateApi.priceInclTax(Money.of(1_234), new BigDecimal("8"))).isEqualTo(Money.of(1_332));
    }

    // ============================== 補助 ==============================

    /**
     * 税区分を登録し、税率の一覧を設定して、その ID を返す
     */
    private UUID createWithRates(Rate... rates) {
        var id = commandService.createTaxCategory(new CreateTaxCategoryCommand("標準"));
        commandService.replaceTaxRates(new ReplaceTaxRatesCommand(id, 0, List.of(rates)));
        return id;
    }
}
