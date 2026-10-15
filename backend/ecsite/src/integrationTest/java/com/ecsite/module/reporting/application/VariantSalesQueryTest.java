package com.ecsite.module.reporting.application;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.ReportTestData;
import com.ecsite.module.reporting.domain.Basis;
import com.ecsite.module.reporting.domain.ReportPeriod;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * バリエーションの売れ行きの統合テスト（T0211、AC-202〜204）。<br />
 * 商品とバリアントは商品カタログの API で作り、写しは DB に直接入れる。2031 年の日付を使う。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class VariantSalesQueryTest {

    /**
     * 2031 年 4 月
     */
    private static final ReportPeriod PERIOD = ReportPeriod.of(LocalDate.parse("2031-04-01"), LocalDate.parse("2031-04-30"));

    @Autowired
    VariantSalesQueryService service;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    private CatalogTestData.TestProduct shirt;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(Instant.parse("2026-10-07T15:00:00Z"));
        var catalog = new CatalogTestData(mockMvc, clock);
        shirt = catalog.product(catalog.taxCategory("標準", "10"), "T シャツ", 1000, "色", "サイズ")
                .variant("白", "M").variant("白", "L").variant("黒", "M");
        var whiteM = shirt.variantIds.get(0);
        var blackM = shirt.variantIds.get(2);

        var data = new ReportTestData(dsl);
        data.order("ACCEPTED", "2031-04-05").line(shirt.id, whiteM, 2).insert();
        data.order("SHIPPED", "2031-04-06").shippedOn("2031-04-08").line(shirt.id, whiteM, 1).line(shirt.id, blackM, 5).insert();
        // 数えない：キャンセル、期間の外
        data.order("CANCELLED", "2031-04-07").line(shirt.id, blackM, 9).insert();
        data.order("ACCEPTED", "2031-05-01").line(shirt.id, blackM, 9).insert();
    }

    @Test
    @DisplayName("AC-202〜204 バリアントごとの販売数量と売上額を、販売数量の多い順に返す。売れなかったバリアントは 0")
    void variantsByQuantity() {
        var report = service.variants(new VariantSalesQuery(shirt.id, PERIOD, Basis.ORDER, false));

        assertThat(report.variants())
                .extracting(VariantSales::variantId, VariantSales::quantity, VariantSales::amount)
                .containsExactly(
                        tuple(shirt.variantIds.get(2), 5L, Money.of(5500)),
                        tuple(shirt.variantIds.get(0), 3L, Money.of(3300)),
                        tuple(shirt.variantIds.get(1), 0L, Money.ZERO));
        assertThat(report.variants().getFirst().axisValues())
                .extracting(VariantSales.AxisValue::name, VariantSales.AxisValue::value)
                .containsExactly(tuple("色", "黒"), tuple("サイズ", "M"));
        assertThat(report.variants().getFirst().variantCode()).isEqualTo(shirt.variantCodes.get(2));
    }

    @Test
    @DisplayName("BR-26 消費税を除くと、税抜小計を合計する")
    void excludesTax() {
        var report = service.variants(new VariantSalesQuery(shirt.id, PERIOD, Basis.ORDER, true));

        assertThat(report.variants()).extracting(VariantSales::amount)
                .containsExactly(Money.of(5000), Money.of(3000), Money.ZERO);
    }

    @Test
    @DisplayName("BR-25 出荷基準では、出荷済みの注文だけを出荷日で数える")
    void shipmentBasis() {
        var report = service.variants(new VariantSalesQuery(shirt.id, PERIOD, Basis.SHIPMENT, false));

        assertThat(report.variants()).extracting(VariantSales::variantId, VariantSales::quantity)
                .containsExactly(
                        tuple(shirt.variantIds.get(2), 5L),
                        tuple(shirt.variantIds.get(0), 1L),
                        tuple(shirt.variantIds.get(1), 0L));
    }

    @Test
    @DisplayName("販売数量が同じなら、商品カタログの並びの順")
    void tiesInCatalogOrder() {
        var report = service.variants(new VariantSalesQuery(shirt.id,
                ReportPeriod.of(LocalDate.parse("2031-06-01"), LocalDate.parse("2031-06-30")), Basis.ORDER, false));

        assertThat(report.variants()).extracting(VariantSales::variantId).containsExactlyElementsOf(shirt.variantIds);
    }

    @Test
    @DisplayName("ない商品は 404")
    void unknownProduct() {
        assertBusinessError(() -> service.variants(new VariantSalesQuery(UUID.randomUUID(), PERIOD, Basis.ORDER, false)),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("BR-26 バリエーションの売れ行きは送料を含めない（送料を除いた印を返す）")
    void shippingExcluded() {
        assertThat(service.variants(new VariantSalesQuery(shirt.id, PERIOD, Basis.ORDER, false)).shippingExcluded()).isTrue();
        assertThat(List.of(service.variants(new VariantSalesQuery(shirt.id, PERIOD, Basis.ORDER, false)).productName()))
                .containsExactly("T シャツ");
    }
}
