package com.ecsite.module.reporting.application;

import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.ReportTestData;
import com.ecsite.module.reporting.domain.AgeGroup;
import com.ecsite.module.reporting.domain.Basis;
import com.ecsite.module.reporting.domain.ReportGender;
import com.ecsite.module.reporting.domain.ReportPeriod;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 地域・年齢層・性別の統合テスト（T0213、AC-205〜208、219、220、BR-27、29）。<br />
 * 写しを DB に直接入れて集計する。2031 年の日付を使う。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class CustomerBreakdownQueryTest {

    /**
     * 2031 年 4 月
     */
    private static final ReportPeriod PERIOD = ReportPeriod.of(LocalDate.parse("2031-04-01"), LocalDate.parse("2031-04-30"));

    @Autowired
    CustomerBreakdownQueryService service;

    @Autowired
    DSLContext dsl;

    private final UUID productA = UUID.randomUUID();

    private final UUID productB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var data = new ReportTestData(dsl);
        // 総額は、明細の税込小計（1 個 1,100 円）＋ 送料 1,000 円
        data.order("ACCEPTED", "2031-04-05").prefecture("13").buyer(36, "FEMALE").line(productA, UUID.randomUUID(), 2).insert();
        data.order("SHIPPED", "2031-04-06").shippedOn("2031-04-08").prefecture("01").buyer(25, "MALE")
                .line(productA, UUID.randomUUID(), 1).line(productB, UUID.randomUUID(), 1).insert();
        data.order("ACCEPTED", "2031-04-07").prefecture("27").buyer(null, "UNDISCLOSED").line(productB, UUID.randomUUID(), 1).insert();
        data.order("CANCELLATION_REQUESTED", "2031-04-08").prefecture("13").buyer(65, null).line(productA, UUID.randomUUID(), 1).insert();
        data.order("ACCEPTED", "2031-04-09").prefecture(null).buyer(15, "OTHER").line(productA, UUID.randomUUID(), 1).insert();
        // 数えない：キャンセル
        data.order("CANCELLED", "2031-04-10").prefecture("14").buyer(40, "MALE").line(productA, UUID.randomUUID(), 9).insert();
    }

    @Test
    @DisplayName("AC-205、220 都道府県ごとの売上額と注文件数を、全国地方公共団体コードの順に返す。コードのない注文は不明として最後")
    void byPrefecture() {
        var report = service.breakdown(query(null));

        assertThat(report.prefectures())
                .extracting(PrefectureSales::prefectureCode, PrefectureSales::amount, PrefectureSales::orders)
                .containsExactly(
                        tuple("01", Money.of(3200), 1L),
                        tuple("13", Money.of(3200 + 2100), 2L),
                        tuple("27", Money.of(2100), 1L),
                        tuple(null, Money.of(2100), 1L));
        assertThat(report.shippingExcluded()).isFalse();
    }

    @Test
    @DisplayName("AC-206、208、BR-27 年齢層ごと。売れなかった層も 0、年齢のない注文は不明")
    void byAgeGroup() {
        var report = service.breakdown(query(null));

        assertThat(report.ageGroups())
                .extracting(AgeGroupSales::ageGroup, AgeGroupSales::amount, AgeGroupSales::orders)
                .containsExactly(
                        tuple(AgeGroup.TEENS_OR_YOUNGER, Money.of(2100), 1L),
                        tuple(AgeGroup.TWENTIES, Money.of(3200), 1L),
                        tuple(AgeGroup.THIRTIES, Money.of(3200), 1L),
                        tuple(AgeGroup.FORTIES, Money.ZERO, 0L),
                        tuple(AgeGroup.FIFTIES, Money.ZERO, 0L),
                        tuple(AgeGroup.SIXTIES_OR_OLDER, Money.of(2100), 1L),
                        tuple(AgeGroup.UNKNOWN, Money.of(2100), 1L));
    }

    @Test
    @DisplayName("AC-219、BR-29 性別ごと。性別のない注文と「回答しない」は不明にまとめる")
    void byGender() {
        var report = service.breakdown(query(null));

        assertThat(report.genders())
                .extracting(GenderSales::gender, GenderSales::amount, GenderSales::orders)
                .containsExactly(
                        tuple(ReportGender.MALE, Money.of(3200), 1L),
                        tuple(ReportGender.FEMALE, Money.of(3200), 1L),
                        tuple(ReportGender.OTHER, Money.of(2100), 1L),
                        tuple(ReportGender.UNKNOWN, Money.of(2100 + 2100), 2L));
    }

    @Test
    @DisplayName("AC-207 商品を選ぶと、その商品を含む注文だけを数え、売上額はその商品の明細の分だけ（送料を除いた印）")
    void selectsProduct() {
        var report = service.breakdown(query(productA));

        assertThat(report.prefectures())
                .extracting(PrefectureSales::prefectureCode, PrefectureSales::amount, PrefectureSales::orders)
                .containsExactly(
                        tuple("01", Money.of(1100), 1L),
                        tuple("13", Money.of(2200 + 1100), 2L),
                        tuple(null, Money.of(1100), 1L));
        assertThat(report.shippingExcluded()).isTrue();
    }

    @Test
    @DisplayName("BR-25 出荷基準では、出荷済みの注文だけを出荷日で数える")
    void shipmentBasis() {
        var report = service.breakdown(new CustomerBreakdownQuery(PERIOD, Basis.SHIPMENT, null, false, false));

        assertThat(report.prefectures()).extracting(PrefectureSales::prefectureCode, PrefectureSales::orders)
                .containsExactly(tuple("01", 1L));
    }

    @Test
    @DisplayName("BR-26 消費税・送料を除ける")
    void excludesTaxAndShipping() {
        var report = service.breakdown(new CustomerBreakdownQuery(PERIOD, Basis.ORDER, null, true, true));

        // 東京都：明細の税抜小計 2,000 ＋ 1,000
        assertThat(report.prefectures()).filteredOn(row -> "13".equals(row.prefectureCode()))
                .extracting(PrefectureSales::amount).containsExactly(Money.of(3000));
    }

    private static CustomerBreakdownQuery query(UUID productId) {
        return new CustomerBreakdownQuery(PERIOD, Basis.ORDER, productId, false, false);
    }
}
