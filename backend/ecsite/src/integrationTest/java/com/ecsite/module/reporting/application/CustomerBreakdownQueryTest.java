package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.model.query.CustomerBreakdownQuery;
import com.ecsite.module.reporting.application.model.view.AgeGroupSales;
import com.ecsite.module.reporting.application.model.view.GenderSales;
import com.ecsite.module.reporting.application.model.view.PrefectureSales;
import com.ecsite.module.reporting.domain.sales.AgeGroup;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportGender;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.support.ReportTestData;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static com.ecsite.module.reporting.support.ReportTestData.lineExclTax;
import static com.ecsite.module.reporting.support.ReportTestData.lineInclTax;
import static com.ecsite.module.reporting.support.ReportTestData.total;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 地域・年齢層・性別の統合テスト
 *
 * <p>写しを DB に直接入れて集計する。他のテストの写しと重ならないよう、2031 年の日付を使う。テストごとにロールバックする。
 *
 * <p>金額の期待値は {@link ReportTestData} の単価と送料の定数から、関数（{@code total} など）で作る。
 */
@IntegrationTest
@Transactional
class CustomerBreakdownQueryTest {

    // ============================== 定数 ==============================

    /**
     * 集計の期間
     *
     * <p>2031 年 4 月。
     */
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.parse("2031-04-01"), LocalDate.parse("2031-04-30"));

    // ============================== Bean ==============================

    @Autowired
    CustomerBreakdownQueryService service;

    @Autowired
    DSLContext dsl;

    /**
     * 商品 A の ID
     */
    private final UUID productA = UUID.randomUUID();

    /**
     * 商品 B の ID
     */
    private final UUID productB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var data = new ReportTestData(dsl);
        // 東京都・36 歳・女性：商品 A 2 個（3,200 円）
        data.order("ACCEPTED", "2031-04-05").prefecture("13").buyer(36, "FEMALE").line(productA, UUID.randomUUID(), 2).insert();
        // 北海道・25 歳・男性：商品 A と B を 1 個ずつ（3,200 円）。出荷済み
        data.order("SHIPPED", "2031-04-06").shippedOn("2031-04-08").prefecture("01").buyer(25, "MALE")
                .line(productA, UUID.randomUUID(), 1).line(productB, UUID.randomUUID(), 1).insert();
        // 大阪府・年齢なし・回答しない：商品 B 1 個（2,100 円）
        data.order("ACCEPTED", "2031-04-07").prefecture("27").buyer(null, "UNDISCLOSED").line(productB, UUID.randomUUID(), 1).insert();
        // 東京都・65 歳・性別なし：商品 A 1 個（2,100 円）。キャンセル申請中
        data.order("CANCELLATION_REQUESTED", "2031-04-08").prefecture("13").buyer(65, null).line(productA, UUID.randomUUID(), 1).insert();
        // 都道府県なし・15 歳・その他：商品 A 1 個（2,100 円）
        data.order("ACCEPTED", "2031-04-09").prefecture(null).buyer(15, "OTHER").line(productA, UUID.randomUUID(), 1).insert();
        // 数えない：キャンセル
        data.order("CANCELLED", "2031-04-10").prefecture("14").buyer(40, "MALE").line(productA, UUID.randomUUID(), 9).insert();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("都道府県")
    class Prefectures {

        @Test
        @DisplayName("AC-205、220 都道府県ごとの売上額と注文件数を、全国地方公共団体コードの順に返し、コードのない注文は不明として最後に置く")
        void byPrefecture() {
            // 操作：注文基準で、全商品を集計する
            var report = service.breakdown(query(null));

            // 確認：北海道・東京都・大阪府の順の後に、不明。キャンセルの神奈川県はない
            assertThat(report.prefectures())
                    .extracting(PrefectureSales::prefectureCode, PrefectureSales::amount, PrefectureSales::orders)
                    .containsExactly(
                            tuple("01", Money.of(total(2)), 1L),
                            tuple("13", Money.of(total(2) + total(1)), 2L),
                            tuple("27", Money.of(total(1)), 1L),
                            tuple(null, Money.of(total(1)), 1L));
        }
    }

    @Nested
    @DisplayName("年齢層")
    class AgeGroups {

        @Test
        @DisplayName("AC-206、BR-27 年齢層ごとの売上額と注文件数を、売れなかった層も 0 として、全ての層で返す")
        void byAgeGroup() {
            // 操作：注文基準で、全商品を集計する
            var report = service.breakdown(query(null));

            // 確認：若い順の全ての層と不明。40 代と 50 代は 0
            assertThat(report.ageGroups())
                    .extracting(AgeGroupSales::ageGroup, AgeGroupSales::amount, AgeGroupSales::orders)
                    .containsExactly(
                            tuple(AgeGroup.TEENS_OR_YOUNGER, Money.of(total(1)), 1L),
                            tuple(AgeGroup.TWENTIES, Money.of(total(2)), 1L),
                            tuple(AgeGroup.THIRTIES, Money.of(total(2)), 1L),
                            tuple(AgeGroup.FORTIES, Money.ZERO, 0L),
                            tuple(AgeGroup.FIFTIES, Money.ZERO, 0L),
                            tuple(AgeGroup.SIXTIES_OR_OLDER, Money.of(total(1)), 1L),
                            tuple(AgeGroup.UNKNOWN, Money.of(total(1)), 1L));
        }

        @Test
        @DisplayName("AC-208 年齢の分からない注文は、年齢層「不明」として数える")
        void countsUnknownAge() {
            // 操作：注文基準で、全商品を集計する
            var report = service.breakdown(query(null));

            // 確認：年齢のない大阪府の注文（2,100 円）が「不明」に入る
            assertThat(report.ageGroups()).filteredOn(row -> row.ageGroup() == AgeGroup.UNKNOWN)
                    .extracting(AgeGroupSales::amount, AgeGroupSales::orders)
                    .containsExactly(tuple(Money.of(total(1)), 1L));
        }
    }

    @Nested
    @DisplayName("性別")
    class Genders {

        @Test
        @DisplayName("AC-219、BR-29 性別ごとの売上額と注文件数を返し、性別のない注文と「回答しない」は不明にまとめる")
        void byGender() {
            // 操作：注文基準で、全商品を集計する
            var report = service.breakdown(query(null));

            // 確認：男性・女性・その他・不明の順。不明は、性別なしと「回答しない」の 2 件
            assertThat(report.genders())
                    .extracting(GenderSales::gender, GenderSales::amount, GenderSales::orders)
                    .containsExactly(
                            tuple(ReportGender.MALE, Money.of(total(2)), 1L),
                            tuple(ReportGender.FEMALE, Money.of(total(2)), 1L),
                            tuple(ReportGender.OTHER, Money.of(total(1)), 1L),
                            tuple(ReportGender.UNKNOWN, Money.of(total(1) + total(1)), 2L));
        }
    }

    @Nested
    @DisplayName("条件")
    class Conditions {

        @Test
        @DisplayName("BR-26 商品を選ばなければ、送料を除いた印を付けない")
        void includesShippingWithoutProduct() {
            // 操作：全商品を集計する
            var report = service.breakdown(query(null));

            // 確認：送料を含めた売上額
            assertThat(report.shippingExcluded()).isFalse();
        }

        @Test
        @DisplayName("AC-207 商品を選ぶと、その商品を含む注文だけを数え、売上額はその商品の明細の分だけ")
        void selectsProduct() {
            // 操作：商品 A を選んで集計する
            var report = service.breakdown(query(productA));

            // 確認：商品 B だけの大阪府はなく、北海道は商品 A の 1 個分（送料なし）
            assertThat(report.prefectures())
                    .extracting(PrefectureSales::prefectureCode, PrefectureSales::amount, PrefectureSales::orders)
                    .containsExactly(
                            tuple("01", Money.of(lineInclTax(1)), 1L),
                            tuple("13", Money.of(lineInclTax(2) + lineInclTax(1)), 2L),
                            tuple(null, Money.of(lineInclTax(1)), 1L));
        }

        @Test
        @DisplayName("AC-207、BR-26 商品を選ぶと、送料を除いた印を付ける")
        void marksShippingExcludedForProduct() {
            // 操作：商品 A を選んで集計する
            var report = service.breakdown(query(productA));

            // 確認：送料を除いた売上額である印
            assertThat(report.shippingExcluded()).isTrue();
        }

        @Test
        @DisplayName("BR-25 出荷基準では、出荷済みの注文だけを出荷日で数える")
        void shipmentBasis() {
            // 操作：出荷基準で集計する
            var report = service.breakdown(new CustomerBreakdownQuery(PERIOD, Basis.SHIPMENT, null, false, false));

            // 確認：出荷済みの北海道の 1 件だけ
            assertThat(report.prefectures()).extracting(PrefectureSales::prefectureCode, PrefectureSales::orders)
                    .containsExactly(tuple("01", 1L));
        }

        @Test
        @DisplayName("BR-26 消費税と送料を除くと、明細の税抜小計だけを数える")
        void excludesTaxAndShipping() {
            // 操作：消費税と送料を除いて集計する
            var report = service.breakdown(new CustomerBreakdownQuery(PERIOD, Basis.ORDER, null, true, true));

            // 確認：東京都は、明細の税抜小計 2,000 円 ＋ 1,000 円
            assertThat(report.prefectures()).filteredOn(row -> "13".equals(row.prefectureCode()))
                    .extracting(PrefectureSales::amount).containsExactly(Money.of(lineExclTax(2) + lineExclTax(1)));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文基準で、消費税と送料を含めて集計する問い合わせを作る。
     *
     * @param productId 選ぶ商品の ID。null なら全商品
     * @return 問い合わせ
     */
    private static CustomerBreakdownQuery query(UUID productId) {
        return new CustomerBreakdownQuery(PERIOD, Basis.ORDER, productId, false, false);
    }
}
