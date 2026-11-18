package com.ecsite.module.reporting.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.model.query.SalesTrendQuery;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.Granularity;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.SalesScope;
import com.ecsite.module.reporting.domain.sales.TrendPoint;
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
import static com.ecsite.module.reporting.support.ReportTestData.totalExclTax;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 売上の推移の統合テスト
 *
 * <p>写しを DB に直接入れて集計する。他のテストの写しと重ならないよう、2031 年の日付を使う。テストごとにロールバックする。
 *
 * <p>金額の期待値は {@link ReportTestData} の単価と送料の定数から、関数（{@code total} など）で作る。
 */
@IntegrationTest
@Transactional
class SalesTrendQueryTest {

    // ============================== 定数 ==============================

    /**
     * 集計の期間
     *
     * <p>2031 年 4〜6 月。
     */
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.parse("2031-04-01"), LocalDate.parse("2031-06-30"));

    // ============================== Bean ==============================

    @Autowired
    SalesTrendQueryService service;

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
        // 受付：商品 A 2 個。総額 3,200（消費税 290、送料 1,000、送料の消費税 90）
        data.order("ACCEPTED", "2031-04-05").line(productA, UUID.randomUUID(), 2).insert();
        // 出荷済み：商品 A 1 個。総額 2,100（消費税 190）。出荷日は 5 月
        data.order("SHIPPED", "2031-04-10").shippedOn("2031-05-02").line(productA, UUID.randomUUID(), 1).insert();
        // 数えない：キャンセル、支払待ち、出荷の後のキャンセル（返送）
        data.order("CANCELLED", "2031-04-12").line(productA, UUID.randomUUID(), 5).insert();
        data.order("PENDING_PAYMENT", "2031-04-15").line(productA, UUID.randomUUID(), 5).insert();
        data.order("CANCELLED", "2031-04-20").shippedOn("2031-04-22").line(productA, UUID.randomUUID(), 5).insert();
        // キャンセル申請中：商品 B 3 個。総額 4,300（消費税 390）
        data.order("CANCELLATION_REQUESTED", "2031-05-03").line(productB, UUID.randomUUID(), 3).insert();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("売上の数え方")
    class Bases {

        @Test
        @DisplayName("AC-197、201、BR-25 注文基準では、受付・キャンセル申請中・出荷済みを注文日の月で数え、売れなかった月は 0")
        void orderBasis() {
            // 操作：注文基準で、月ごとに集計する
            var report = service.trend(query(Basis.ORDER, null, false, false));

            // 確認：4 月は受付と出荷済み、5 月はキャンセル申請中、6 月は 0
            assertThat(report.points()).extracting(point -> point.bucket().start(), TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(
                            tuple(LocalDate.parse("2031-04-01"), Money.of(total(2) + total(1)), 3L),
                            tuple(LocalDate.parse("2031-05-01"), Money.of(total(3)), 3L),
                            tuple(LocalDate.parse("2031-06-01"), Money.ZERO, 0L));
        }

        @Test
        @DisplayName("AC-200、BR-25 出荷基準では、出荷済みだけを出荷日で数える（出荷の後のキャンセルは数えない）")
        void shipmentBasis() {
            // 操作：出荷基準で、月ごとに集計する
            var report = service.trend(query(Basis.SHIPMENT, null, false, false));

            // 確認：出荷日の 5 月に、出荷済みの 1 件だけ
            assertThat(report.points()).extracting(TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(tuple(Money.ZERO, 0L), tuple(Money.of(total(1)), 1L), tuple(Money.ZERO, 0L));
        }
    }

    @Nested
    @DisplayName("売上額")
    class Amounts {

        @Test
        @DisplayName("BR-26 商品を選ばず、何も除かなければ、送料を除いた印を付けない")
        void includesShippingByDefault() {
            // 操作：全商品を、何も除かずに集計する
            var report = service.trend(query(Basis.ORDER, null, false, false));

            // 確認：送料を含めた売上額
            assertThat(report.shippingExcluded()).isFalse();
        }

        @Test
        @DisplayName("BR-26 消費税を除くと、総額から消費税の合計を引く")
        void excludesTax() {
            // 操作：消費税を除いて集計する
            var april = service.trend(query(Basis.ORDER, null, true, false)).points().getFirst();

            // 確認：4 月の 2 件の、総額から消費税を引いた額の合計
            assertThat(april.amount()).isEqualTo(Money.of(totalExclTax(2) + totalExclTax(1)));
        }

        @Test
        @DisplayName("BR-26 送料を除くと、総額から送料を引く")
        void excludesShipping() {
            // 操作：送料を除いて集計する
            var april = service.trend(query(Basis.ORDER, null, false, true)).points().getFirst();

            // 確認：4 月の 2 件の、明細の税込小計の合計
            assertThat(april.amount()).isEqualTo(Money.of(lineInclTax(2) + lineInclTax(1)));
        }

        @Test
        @DisplayName("BR-26 消費税と送料を除くと、税抜の送料を引く")
        void excludesTaxAndShipping() {
            // 操作：消費税と送料を除いて集計する
            var april = service.trend(query(Basis.ORDER, null, true, true)).points().getFirst();

            // 確認：4 月の 2 件の、明細の税抜小計の合計
            assertThat(april.amount()).isEqualTo(Money.of(lineExclTax(2) + lineExclTax(1)));
        }
    }

    @Nested
    @DisplayName("商品の選択")
    class ProductSelection {

        @Test
        @DisplayName("AC-199 商品を選ばなければ、全商品の注文を数える")
        void countsAllProductsWithoutSelection() {
            // 操作：商品を選ばずに集計する
            var report = service.trend(query(Basis.ORDER, null, false, false));

            // 確認：商品 B だけの 5 月の注文も数える
            assertThat(report.points().get(1)).extracting(TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(Money.of(total(3)), 3L);
        }

        @Test
        @DisplayName("AC-199、BR-26 商品を選ぶと、その商品の明細だけを数え、送料は含めない")
        void selectsProduct() {
            // 操作：商品 A を選んで集計する
            var report = service.trend(query(Basis.ORDER, productA, false, false));

            // 確認：4 月の商品 A の明細の税込小計だけ。商品 B の 5 月は 0
            assertThat(report.points()).extracting(TrendPoint::amount, TrendPoint::quantity)
                    .containsExactly(tuple(Money.of(lineInclTax(3)), 3L), tuple(Money.ZERO, 0L), tuple(Money.ZERO, 0L));
        }

        @Test
        @DisplayName("AC-199、BR-26 商品を選ぶと、送料を除いた印を付ける")
        void marksShippingExcludedForProduct() {
            // 操作：商品 A を選んで集計する
            var report = service.trend(query(Basis.ORDER, productA, false, false));

            // 確認：送料を除いた売上額である印
            assertThat(report.shippingExcluded()).isTrue();
        }

        @Test
        @DisplayName("BR-26 商品を選ぶと、送料を除く指定の真偽によらず、同じ売上額で送料を除いた印を付ける")
        void ignoresShippingSettingForProduct() {
            // 操作：商品 A を選び、送料を除く指定を真と偽にして集計する
            var excluded = service.trend(query(Basis.ORDER, productA, false, true));
            var included = service.trend(query(Basis.ORDER, productA, false, false));

            // 確認：売上額が同じで、どちらも送料を除いた印を付ける
            assertThat(excluded.points()).isEqualTo(included.points());
            assertThat(excluded.shippingExcluded()).isTrue();
        }

        @Test
        @DisplayName("BR-26 商品を選んで消費税を除くと、その商品の明細の税抜小計だけを数える")
        void selectsProductExcludingTax() {
            // 操作：商品 A を選び、消費税を除いて集計する
            var april = service.trend(query(Basis.ORDER, productA, true, false)).points().getFirst();

            // 確認：4 月の商品 A の明細の税抜小計
            assertThat(april.amount()).isEqualTo(Money.of(lineExclTax(3)));
        }
    }

    @Nested
    @DisplayName("期間の境界")
    class PeriodBoundary {

        /**
         * 境界を確認する商品の ID（準備の他の注文と分ける）
         */
        private final UUID boundaryProduct = UUID.randomUUID();

        @Test
        @DisplayName("BR-24 注文基準では、期間の始まりの日と終わりの日の注文を数え、その前日と翌日の注文は数えない")
        void orderBasisIncludesBothEnds() {
            // 準備：期間の前日・始まりの日・終わりの日・翌日の注文（数量で見分ける）
            var data = new ReportTestData(dsl);
            data.order("ACCEPTED", "2031-03-31").line(boundaryProduct, UUID.randomUUID(), 10).insert();
            data.order("ACCEPTED", "2031-04-01").line(boundaryProduct, UUID.randomUUID(), 1).insert();
            data.order("ACCEPTED", "2031-06-30").line(boundaryProduct, UUID.randomUUID(), 2).insert();
            data.order("ACCEPTED", "2031-07-01").line(boundaryProduct, UUID.randomUUID(), 20).insert();

            // 操作：その商品を選び、注文基準で集計する
            var report = service.trend(query(Basis.ORDER, boundaryProduct, false, false));

            // 確認：4 月は始まりの日の 1 個、6 月は終わりの日の 2 個だけ
            assertThat(report.points()).extracting(TrendPoint::quantity).containsExactly(1L, 0L, 2L);
        }

        @Test
        @DisplayName("BR-24 出荷基準では、期間の始まりの日と終わりの日の出荷を数え、その前日と翌日の出荷は数えない")
        void shipmentBasisIncludesBothEnds() {
            // 準備：期間の前に注文し、期間の前日・始まりの日・終わりの日・翌日に出荷した注文（数量で見分ける）
            var data = new ReportTestData(dsl);
            data.order("SHIPPED", "2031-03-01").shippedOn("2031-03-31").line(boundaryProduct, UUID.randomUUID(), 10).insert();
            data.order("SHIPPED", "2031-03-01").shippedOn("2031-04-01").line(boundaryProduct, UUID.randomUUID(), 1).insert();
            data.order("SHIPPED", "2031-03-01").shippedOn("2031-06-30").line(boundaryProduct, UUID.randomUUID(), 2).insert();
            data.order("SHIPPED", "2031-03-01").shippedOn("2031-07-01").line(boundaryProduct, UUID.randomUUID(), 20).insert();

            // 操作：その商品を選び、出荷基準で集計する
            var report = service.trend(query(Basis.SHIPMENT, boundaryProduct, false, false));

            // 確認：4 月は始まりの日の 1 個、6 月は終わりの日の 2 個だけ
            assertThat(report.points()).extracting(TrendPoint::quantity).containsExactly(1L, 0L, 2L);
        }
    }

    @Test
    @DisplayName("AC-197 粒度を選べる（四半期は会計年度の区切り）")
    void granularity() {
        // 操作：四半期で集計する
        var report = service.trend(new SalesTrendQuery(PERIOD, Granularity.QUARTER, Basis.ORDER, new SalesScope(null, false, false)));

        // 確認：4〜6 月が 2031 年度の第 1 四半期の 1 行
        assertThat(report.points()).extracting(point -> point.bucket().fiscalYear(), point -> point.bucket().quarter(),
                        TrendPoint::amount)
                .containsExactly(tuple(2031, 1, Money.of(total(2) + total(1) + total(3))));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 月ごとに集計する問い合わせを作る。
     *
     * @param basis           売上の数え方
     * @param productId       選ぶ商品の ID。null なら全商品
     * @param excludeTax      消費税を除くか
     * @param excludeShipping 送料を除くか
     * @return 問い合わせ
     */
    private static SalesTrendQuery query(Basis basis, UUID productId, boolean excludeTax, boolean excludeShipping) {
        return new SalesTrendQuery(PERIOD, Granularity.MONTH, basis, new SalesScope(productId, excludeTax, excludeShipping));
    }
}
