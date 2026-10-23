package com.ecsite.module.reporting.application;

import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.ReportTestData;
import com.ecsite.module.reporting.application.model.query.SalesTrendQuery;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.Granularity;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.domain.sales.TrendPoint;
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
 * 売上の推移の統合テスト（T0209、AC-197〜201、BR-25、26）。<br />
 * 写しを DB に直接入れて集計する。他のテストの写しと重ならないよう、2031 年の日付を使う。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class SalesTrendQueryTest {

    /**
     * 2031 年 4〜6 月
     */
    private static final ReportPeriod PERIOD = ReportPeriod.of(LocalDate.parse("2031-04-01"), LocalDate.parse("2031-06-30"));

    @Autowired
    SalesTrendQueryService service;

    @Autowired
    DSLContext dsl;

    private final UUID productA = UUID.randomUUID();

    private final UUID productB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var data = new ReportTestData(dsl);
        // 受付：総額 3,200（消費税 290、送料 1,000、送料の消費税 90）
        data.order("ACCEPTED", "2031-04-05").line(productA, UUID.randomUUID(), 2).insert();
        // 出荷済み：総額 2,100（消費税 190）。出荷日は 5 月
        data.order("SHIPPED", "2031-04-10").shippedOn("2031-05-02").line(productA, UUID.randomUUID(), 1).insert();
        // 数えない：キャンセル、支払待ち、出荷の後のキャンセル（返送）
        data.order("CANCELLED", "2031-04-12").line(productA, UUID.randomUUID(), 5).insert();
        data.order("PENDING_PAYMENT", "2031-04-15").line(productA, UUID.randomUUID(), 5).insert();
        data.order("CANCELLED", "2031-04-20").shippedOn("2031-04-22").line(productA, UUID.randomUUID(), 5).insert();
        // キャンセル申請中：総額 4,300（消費税 390）
        data.order("CANCELLATION_REQUESTED", "2031-05-03").line(productB, UUID.randomUUID(), 3).insert();
    }

    @Test
    @DisplayName("AC-197、201、BR-25 注文基準：受付・キャンセル申請中・出荷済みを注文日で数え、総額を合計する。売れなかった月は 0")
    void orderBasis() {
        var report = service.trend(query(Basis.ORDER, null, false, false));

        assertThat(report.points()).extracting(point -> point.bucket().start(), TrendPoint::amount, TrendPoint::quantity)
                .containsExactly(
                        tuple(LocalDate.parse("2031-04-01"), Money.of(5300), 3L),
                        tuple(LocalDate.parse("2031-05-01"), Money.of(4300), 3L),
                        tuple(LocalDate.parse("2031-06-01"), Money.ZERO, 0L));
        assertThat(report.shippingExcluded()).isFalse();
    }

    @Test
    @DisplayName("AC-200、BR-25 出荷基準：出荷済みだけを出荷日で数える（出荷の後のキャンセルは数えない）")
    void shipmentBasis() {
        var report = service.trend(query(Basis.SHIPMENT, null, false, false));

        assertThat(report.points()).extracting(TrendPoint::amount, TrendPoint::quantity)
                .containsExactly(tuple(Money.ZERO, 0L), tuple(Money.of(2100), 1L), tuple(Money.ZERO, 0L));
    }

    @Test
    @DisplayName("BR-26 消費税を除くと、総額から消費税の合計を引く")
    void excludesTax() {
        var april = service.trend(query(Basis.ORDER, null, true, false)).points().getFirst();

        assertThat(april.amount()).isEqualTo(Money.of((3200 - 290) + (2100 - 190)));
    }

    @Test
    @DisplayName("BR-26 送料を除くと送料を、消費税と送料を除くと税抜の送料を引く")
    void excludesShipping() {
        assertThat(service.trend(query(Basis.ORDER, null, false, true)).points().getFirst().amount())
                .isEqualTo(Money.of(2200 + 1100));
        assertThat(service.trend(query(Basis.ORDER, null, true, true)).points().getFirst().amount())
                .isEqualTo(Money.of(2000 + 1000));
    }

    @Test
    @DisplayName("AC-199、BR-26 商品を選ぶと、その商品の明細だけを数え、送料は含めない（送料を除いた印を返す）")
    void selectsProduct() {
        var report = service.trend(query(Basis.ORDER, productA, false, false));

        assertThat(report.points()).extracting(TrendPoint::amount, TrendPoint::quantity)
                .containsExactly(tuple(Money.of(3300), 3L), tuple(Money.ZERO, 0L), tuple(Money.ZERO, 0L));
        assertThat(report.shippingExcluded()).isTrue();
        assertThat(service.trend(query(Basis.ORDER, productA, true, false)).points().getFirst().amount())
                .isEqualTo(Money.of(3000));
    }

    @Test
    @DisplayName("AC-197 粒度を選べる（四半期は会計年度の区切り）")
    void granularity() {
        var report = service.trend(new SalesTrendQuery(PERIOD, Granularity.QUARTER, Basis.ORDER, null, false, false));

        assertThat(report.points()).extracting(point -> point.bucket().fiscalYear(), point -> point.bucket().quarter(),
                        TrendPoint::amount)
                .containsExactly(tuple(2031, 1, Money.of(9600)));
    }

    private static SalesTrendQuery query(Basis basis, UUID productId, boolean excludeTax, boolean excludeShipping) {
        return new SalesTrendQuery(PERIOD, Granularity.MONTH, basis, productId, excludeTax, excludeShipping);
    }
}
