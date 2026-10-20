package com.ecsite.module.reporting.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.api.admin.model.AgeGroupSales;
import com.ecsite.api.admin.model.CustomerBreakdownReport;
import com.ecsite.api.admin.model.GenderSales;
import com.ecsite.api.admin.model.OrderAxisValue;
import com.ecsite.api.admin.model.PrefectureSales;
import com.ecsite.api.admin.model.SalesTrendPoint;
import com.ecsite.api.admin.model.SalesTrendReport;
import com.ecsite.api.admin.model.VariantSales;
import com.ecsite.api.admin.model.VariantSalesReport;
import com.ecsite.module.reporting.ReportTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * レポートの API の形の統合テスト（T0218、AC-197〜208、219、220、NFR-16）。<br />
 * 集計の規則は application 層のテスト（{@code SalesTrendQueryTest} など）で確かめ、ここは要求の受け取りと応答の形を確かめる。
 * 写しは DB に直接入れる。2031 年の日付を使う。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class ReportApiTest {

    private static final String REPORTS = "/api/admin/reports";

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
        shirt = catalog.product(catalog.taxCategory("標準", "10"), "T シャツ", 1000, "色").variant("白").variant("黒");

        var data = new ReportTestData(dsl);
        // 2031 年 4 月 5 日：白 2 枚、東京都、30 歳の女性（税込 2,200 円と送料 1,000 円）
        data.order("ACCEPTED", "2031-04-05").buyer(30, "FEMALE").line(shirt.id, shirt.variantIds.get(0), 2).insert();
    }

    @Nested
    @DisplayName("売上の推移")
    class SalesTrend {

        @Test
        @DisplayName("AC-197 区切りごとの売上額と販売数量を返す。四半期は会計年度と四半期の番号を付ける。売上の数え方を省けば注文基準")
        void quarters() throws Exception {
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2031-09-30&granularity=QUARTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points", hasSize(2)))
                    .andExpect(jsonPath("$.points[0].start").value("2031-04-01"))
                    .andExpect(jsonPath("$.points[0].end").value("2031-06-30"))
                    .andExpect(jsonPath("$.points[0].fiscalYear").value(2031))
                    .andExpect(jsonPath("$.points[0].quarter").value(1))
                    .andExpect(jsonPath("$.points[0].amount").value(3200))
                    .andExpect(jsonPath("$.points[0].quantity").value(2))
                    .andExpect(jsonPath("$.points[1].amount").value(0))
                    .andExpect(jsonPath("$.shippingExcluded").value(false));
        }

        @Test
        @DisplayName("月の区切りには、会計年度と四半期の番号を付けない")
        void monthsWithoutFiscalYear() throws Exception {
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2031-04-30&granularity=MONTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].fiscalYear").doesNotExist())
                    .andExpect(jsonPath("$.points[0].quarter").doesNotExist());
        }

        @Test
        @DisplayName("AC-198、199 出荷基準、消費税と送料を除く、商品の選択を受け取る")
        void conditions() throws Exception {
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2031-04-30&granularity=MONTH&basis=SHIPMENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].amount").value(0));
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2031-04-30&granularity=MONTH"
                            + "&excludeTax=true&excludeShipping=true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].amount").value(2000))
                    .andExpect(jsonPath("$.shippingExcluded").value(true));
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2031-04-30&granularity=MONTH&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].amount").value(2200))
                    .andExpect(jsonPath("$.shippingExcluded").value(true));
        }

        @Test
        @DisplayName("期間が 5 年を超えると 400")
        void periodTooLong() throws Exception {
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2036-04-01&granularity=YEAR"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PERIOD"))
                    .andExpect(jsonPath("$.message").value("期間は、始まりの日から終わりの日までを 5 年以内にしてください。"));
        }

        @Test
        @DisplayName("始まりが終わりより後なら 400")
        void periodReversed() throws Exception {
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-02&to=2031-04-01&granularity=MONTH"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PERIOD"));
        }

        @Test
        @DisplayName("粒度がなければ 400")
        void granularityRequired() throws Exception {
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2031-04-30"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("バリアントの売れ行き")
    class Variants {

        @Test
        @DisplayName("AC-202、203、204 商品と、バリアントごとの販売数量と売上額を返す。売れなかったバリアントは 0")
        void variants() throws Exception {
            mockMvc.perform(get(REPORTS + "/variant-sales?from=2031-04-01&to=2031-04-30&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(shirt.id.toString()))
                    .andExpect(jsonPath("$.productName").value("T シャツ"))
                    .andExpect(jsonPath("$.variants[*].variantId").value(contains(
                            shirt.variantIds.get(0).toString(), shirt.variantIds.get(1).toString())))
                    .andExpect(jsonPath("$.variants[0].variantCode").value(shirt.variantCodes.get(0)))
                    .andExpect(jsonPath("$.variants[0].axisValues[0].name").value("色"))
                    .andExpect(jsonPath("$.variants[0].axisValues[0].value").value("白"))
                    .andExpect(jsonPath("$.variants[0].quantity").value(2))
                    .andExpect(jsonPath("$.variants[0].amount").value(2200))
                    .andExpect(jsonPath("$.variants[1].quantity").value(0))
                    .andExpect(jsonPath("$.shippingExcluded").value(true));
        }

        @Test
        @DisplayName("商品がなければ 404")
        void unknownProduct() throws Exception {
            mockMvc.perform(get(REPORTS + "/variant-sales?from=2031-04-01&to=2031-04-30&productId=" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("商品がなければ 400（必須）")
        void productRequired() throws Exception {
            mockMvc.perform(get(REPORTS + "/variant-sales?from=2031-04-01&to=2031-04-30"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("地域・年齢層・性別")
    class Customers {

        @Test
        @DisplayName("AC-205、206、207、208、219、220、262 都道府県は売れたものだけを地域の名前を添えて、年齢層と性別はすべての区分を返す")
        void breakdown() throws Exception {
            new ReportTestData(dsl).order("ACCEPTED", "2031-04-06").prefecture(null)
                    .line(shirt.id, shirt.variantIds.get(1), 1).insert();

            mockMvc.perform(get(REPORTS + "/customer-breakdown?from=2031-04-01&to=2031-04-30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prefectures", hasSize(2)))
                    .andExpect(jsonPath("$.prefectures[0].prefectureCode").value("13"))
                    .andExpect(jsonPath("$.prefectures[0].prefectureName").value("東京都"))
                    .andExpect(jsonPath("$.prefectures[0].amount").value(3200))
                    .andExpect(jsonPath("$.prefectures[0].orders").value(1))
                    .andExpect(jsonPath("$.prefectures[1].prefectureCode").doesNotExist())
                    .andExpect(jsonPath("$.prefectures[1].prefectureName").doesNotExist())
                    .andExpect(jsonPath("$.ageGroups[*].ageGroup").value(contains(
                            "TEENS_OR_YOUNGER", "TWENTIES", "THIRTIES", "FORTIES", "FIFTIES", "SIXTIES_OR_OLDER", "UNKNOWN")))
                    .andExpect(jsonPath("$.ageGroups[2].amount").value(3200))
                    .andExpect(jsonPath("$.ageGroups[2].orders").value(1))
                    .andExpect(jsonPath("$.genders[*].gender").value(contains("MALE", "FEMALE", "OTHER", "UNKNOWN")))
                    .andExpect(jsonPath("$.genders[1].orders").value(1))
                    .andExpect(jsonPath("$.genders[3].orders").value(1))
                    .andExpect(jsonPath("$.shippingExcluded").value(false));
        }
    }

    @Test
    @DisplayName("NFR-16 レポートの応答の型は、集計の値だけを持ち、会員を特定できる項目を持たない")
    void responsesHaveNoPersonalData() {
        assertThat(fields(SalesTrendReport.class)).containsExactlyInAnyOrder("points", "shippingExcluded");
        assertThat(fields(SalesTrendPoint.class))
                .containsExactlyInAnyOrder("start", "end", "fiscalYear", "quarter", "amount", "quantity");
        assertThat(fields(VariantSalesReport.class))
                .containsExactlyInAnyOrder("productId", "productName", "variants", "shippingExcluded");
        assertThat(fields(VariantSales.class))
                .containsExactlyInAnyOrder("variantId", "variantCode", "axisValues", "quantity", "amount");
        assertThat(fields(OrderAxisValue.class)).containsExactlyInAnyOrder("name", "value");
        assertThat(fields(CustomerBreakdownReport.class))
                .containsExactlyInAnyOrder("prefectures", "ageGroups", "genders", "shippingExcluded");
        assertThat(fields(PrefectureSales.class)).containsExactlyInAnyOrder("prefectureCode", "prefectureName", "amount", "orders");
        assertThat(fields(AgeGroupSales.class)).containsExactlyInAnyOrder("ageGroup", "amount", "orders");
        assertThat(fields(GenderSales.class)).containsExactlyInAnyOrder("gender", "amount", "orders");
    }

    /**
     * 応答の型の項目（static でないフィールド）の名前
     */
    private static String[] fields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .toArray(String[]::new);
    }
}
