package com.ecsite.module.reporting.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.model.query.VariantSalesQuery;
import com.ecsite.module.reporting.application.model.view.VariantSales;
import com.ecsite.module.reporting.domain.sales.Basis;
import com.ecsite.module.reporting.domain.sales.ReportPeriod;
import com.ecsite.module.reporting.support.ReportTestData;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static com.ecsite.module.reporting.support.ReportTestData.lineExclTax;
import static com.ecsite.module.reporting.support.ReportTestData.lineInclTax;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * バリアントの売れ行きの統合テスト
 *
 * <p>商品とバリアントは商品カタログの API で作り、写しは DB に直接入れる。2031 年の日付を使う。テストごとにロールバックする。
 *
 * <p>明細は {@link ReportTestData} のとおり、1 個 1,100 円（税抜 1,000 円）。
 */
@IntegrationTest
@Transactional
class VariantSalesQueryTest {

    // ============================== 定数 ==============================

    /**
     * 集計の期間
     *
     * <p>2031 年 4 月。
     */
    private static final ReportPeriod PERIOD = ReportPeriod.of(LocalDate.parse("2031-04-01"), LocalDate.parse("2031-04-30"));

    /**
     * T シャツの商品名
     */
    private static final String PRODUCT_NAME = "T シャツ";

    // ============================== Bean ==============================

    @Autowired
    VariantSalesQueryService service;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    /**
     * 売れ行きを見る T シャツ
     *
     * <p>バリアントは、白・M、白・L、黒・M の順。
     */
    private CatalogTestData.TestProduct shirt;

    @BeforeEach
    void setUp() throws Exception {
        var catalog = new CatalogTestData(mockMvc, clock);
        shirt = catalog.product(catalog.taxCategory("標準", "10"), PRODUCT_NAME, 1000, "色", "サイズ")
                .variant("白", "M").variant("白", "L").variant("黒", "M");

        var data = new ReportTestData(dsl);
        // 白・M は 2 ＋ 1 = 3 枚、黒・M は 5 枚（出荷済みの注文）、白・L は売れない
        data.order("ACCEPTED", "2031-04-05").line(shirt.id, whiteM(), 2).insert();
        data.order("SHIPPED", "2031-04-06").shippedOn("2031-04-08").line(shirt.id, whiteM(), 1).line(shirt.id, blackM(), 5).insert();
        // 数えない：キャンセル、期間の外
        data.order("CANCELLED", "2031-04-07").line(shirt.id, blackM(), 9).insert();
        data.order("ACCEPTED", "2031-05-01").line(shirt.id, blackM(), 9).insert();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("バリアントごとの売れ行き")
    class Variants {

        @Test
        @DisplayName("AC-202、204 バリアントごとの販売数量と売上額を返し、売れなかったバリアントは 0")
        void quantityAndAmountPerVariant() {
            // 操作：注文基準で集計する
            var report = service.variants(query(Basis.ORDER, false));

            // 確認：黒・M は 5 枚、白・M は 3 枚、白・L は 0
            assertThat(report.variants())
                    .extracting(VariantSales::variantId, VariantSales::quantity, VariantSales::amount)
                    .containsExactlyInAnyOrder(
                            tuple(blackM(), 5L, Money.of(lineInclTax(5))),
                            tuple(whiteM(), 3L, Money.of(lineInclTax(3))),
                            tuple(whiteL(), 0L, Money.ZERO));
        }

        @Test
        @DisplayName("AC-203 販売数量の多い順に並べる")
        void sortsByQuantity() {
            // 操作：注文基準で集計する
            var report = service.variants(query(Basis.ORDER, false));

            // 確認：黒・M、白・M、白・L の順
            assertThat(report.variants()).extracting(VariantSales::variantId).containsExactly(blackM(), whiteM(), whiteL());
        }

        @Test
        @DisplayName("販売数量が同じなら、商品カタログの並びの順")
        void tiesInCatalogOrder() {
            // 操作：どれも売れていない 6 月を集計する
            var report = service.variants(new VariantSalesQuery(shirt.id,
                    ReportPeriod.of(LocalDate.parse("2031-06-01"), LocalDate.parse("2031-06-30")), Basis.ORDER, false));

            // 確認：商品カタログの並び（白・M、白・L、黒・M）
            assertThat(report.variants()).extracting(VariantSales::variantId).containsExactlyElementsOf(shirt.variantIds);
        }

        @Test
        @DisplayName("AC-202 バリアントに、バリアント品番と軸の値を添える")
        void addsVariantCodeAndAxisValues() {
            // 操作：注文基準で集計する
            var first = service.variants(query(Basis.ORDER, false)).variants().getFirst();

            // 確認：最も売れた黒・M の、品番と軸の値（軸の順）
            assertThat(first.variantCode()).isEqualTo(shirt.variantCodes.get(2));
            assertThat(first.axisValues())
                    .extracting(VariantSales.AxisValue::name, VariantSales.AxisValue::value)
                    .containsExactly(tuple("色", "黒"), tuple("サイズ", "M"));
        }
    }

    @Nested
    @DisplayName("条件")
    class Conditions {

        @Test
        @DisplayName("BR-26 消費税を除くと、税抜小計を合計する")
        void excludesTax() {
            // 操作：消費税を除いて集計する
            var report = service.variants(query(Basis.ORDER, true));

            // 確認：黒・M、白・M、白・L の税抜小計
            assertThat(report.variants()).extracting(VariantSales::amount)
                    .containsExactly(Money.of(lineExclTax(5)), Money.of(lineExclTax(3)), Money.ZERO);
        }

        @Test
        @DisplayName("BR-25 出荷基準では、出荷済みの注文だけを出荷日で数える")
        void shipmentBasis() {
            // 操作：出荷基準で集計する
            var report = service.variants(query(Basis.SHIPMENT, false));

            // 確認：出荷済みの注文の、黒・M 5 枚と白・M 1 枚だけ
            assertThat(report.variants()).extracting(VariantSales::variantId, VariantSales::quantity)
                    .containsExactly(
                            tuple(blackM(), 5L),
                            tuple(whiteM(), 1L),
                            tuple(whiteL(), 0L));
        }

        @Test
        @DisplayName("BR-26 バリアントの売れ行きは送料を含めない（送料を除いた印を返す）")
        void shippingExcluded() {
            // 操作：注文基準で集計する
            var report = service.variants(query(Basis.ORDER, false));

            // 確認：送料を除いた売上額である印
            assertThat(report.shippingExcluded()).isTrue();
        }
    }

    @Nested
    @DisplayName("商品")
    class Product {

        @Test
        @DisplayName("バリアントの売れ行きに、商品名を添える")
        void returnsProductName() {
            // 操作：注文基準で集計する
            var report = service.variants(query(Basis.ORDER, false));

            // 確認：商品カタログの商品名
            assertThat(report.productName()).isEqualTo(PRODUCT_NAME);
        }

        @Test
        @DisplayName("商品カタログにない商品は、見つからない誤り")
        void unknownProduct() {
            // 操作と確認：業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> service.variants(new VariantSalesQuery(UUID.randomUUID(), PERIOD, Basis.ORDER, false)),
                    CommonErrorCode.NOT_FOUND);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * T シャツの、期間の売れ行きの問い合わせを作る。
     *
     * @param basis      売上の数え方
     * @param excludeTax 消費税を除くか
     * @return 問い合わせ
     */
    private VariantSalesQuery query(Basis basis, boolean excludeTax) {
        return new VariantSalesQuery(shirt.id, PERIOD, basis, excludeTax);
    }

    /**
     * 白・M のバリアント ID を返す。
     *
     * @return バリアント ID
     */
    private UUID whiteM() {
        return shirt.variantIds.getFirst();
    }

    /**
     * 白・L のバリアント ID を返す。
     *
     * @return バリアント ID
     */
    private UUID whiteL() {
        return shirt.variantIds.get(1);
    }

    /**
     * 黒・M のバリアント ID を返す。
     *
     * @return バリアント ID
     */
    private UUID blackM() {
        return shirt.variantIds.get(2);
    }
}
