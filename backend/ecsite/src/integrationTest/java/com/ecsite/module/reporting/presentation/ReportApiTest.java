package com.ecsite.module.reporting.presentation;

import com.ecsite.api.admin.model.AgeGroupSales;
import com.ecsite.api.admin.model.CustomerBreakdownReport;
import com.ecsite.api.admin.model.GenderSales;
import com.ecsite.api.admin.model.OrderAxisValue;
import com.ecsite.api.admin.model.PrefectureSales;
import com.ecsite.api.admin.model.SalesTrendPoint;
import com.ecsite.api.admin.model.SalesTrendReport;
import com.ecsite.api.admin.model.VariantSales;
import com.ecsite.api.admin.model.VariantSalesReport;
import com.ecsite.module.reporting.support.ReportTestData;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.ecsite.module.reporting.support.ReportTestData.lineExclTax;
import static com.ecsite.module.reporting.support.ReportTestData.lineInclTax;
import static com.ecsite.module.reporting.support.ReportTestData.total;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * レポートの API の形の統合テスト
 *
 * <p>集計の規則は application 層のテスト（{@code SalesTrendQueryTest} など）で確認し、ここはリクエストの受け取りと応答の形を確認する。
 * 写しは DB に直接入れる。2031 年の日付を使う。テストごとにロールバックする。
 *
 * <p>金額の期待値は {@link ReportTestData} の単価と送料の定数から、関数（{@code total} など）で作る。
 */
@IntegrationTest
@Transactional
class ReportApiTest {

    // ============================== 定数 ==============================

    /**
     * レポートの API のパス
     */
    private static final String REPORTS = "/api/admin/reports";

    /**
     * 2031 年 4 月の期間のクエリ
     */
    private static final String APRIL = "from=2031-04-01&to=2031-04-30";

    /**
     * 2031 年 4〜9 月の期間のクエリ
     *
     * <p>4 月始まりの会計年度の、2031 年度の上半期。
     */
    private static final String FIRST_HALF = "from=2031-04-01&to=2031-09-30";

    /**
     * T シャツの商品名
     */
    private static final String PRODUCT_NAME = "T シャツ";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    /**
     * 商品カタログのテストデータ
     */
    private CatalogTestData catalog;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * 売上のある T シャツ
     *
     * <p>バリアントは白、黒の順。
     */
    private CatalogTestData.TestProduct shirt;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("標準 " + UUID.randomUUID().toString().substring(0, 8), "10");
        shirt = catalog.product(taxCategoryId, PRODUCT_NAME, 1000, "色").variant("白").variant("黒");

        var data = new ReportTestData(dsl);
        // 2031 年 4 月 5 日：白 2 枚、東京都、30 歳の女性（税込 2,200 円と送料 1,000 円）
        data.order("ACCEPTED", "2031-04-05").buyer(30, "FEMALE").line(shirt.id, shirt.variantIds.getFirst(), 2).insert();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("売上の推移")
    class SalesTrend {

        @Test
        @DisplayName("AC-197 区切りごとの販売数量を返し、売れない区切りの売上額は 0。四半期には会計年度と四半期の番号を付ける")
        void quarters() throws Exception {
            // 操作と確認：上半期の 2 つの四半期。売れた第 1 四半期に、2031 年度の 1 と 2 枚。売れない第 2 四半期は 0 円
            mockMvc.perform(get(REPORTS + "/sales-trend?" + FIRST_HALF + "&granularity=QUARTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points", hasSize(2)))
                    .andExpect(jsonPath("$.points[0].start").value("2031-04-01"))
                    .andExpect(jsonPath("$.points[0].end").value("2031-06-30"))
                    .andExpect(jsonPath("$.points[0].fiscalYear").value(2031))
                    .andExpect(jsonPath("$.points[0].quarter").value(1))
                    .andExpect(jsonPath("$.points[0].quantity").value(2))
                    .andExpect(jsonPath("$.points[1].amount").value(0))
                    .andExpect(jsonPath("$.shippingExcluded").value(false));
        }

        @Test
        @DisplayName("AC-200 売上の数え方を省けば、注文基準で数える")
        void orderBasisByDefault() throws Exception {
            // 操作と確認：出荷していない受付の注文の総額 3,200 円を数える
            mockMvc.perform(get(REPORTS + "/sales-trend?" + FIRST_HALF + "&granularity=QUARTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].amount").value(total(2)));
        }

        @Test
        @DisplayName("月の区切りには、会計年度と四半期の番号を付けない")
        void monthsWithoutFiscalYear() throws Exception {
            // 操作と確認：会計年度と四半期の番号の項目がない
            mockMvc.perform(get(REPORTS + "/sales-trend?" + APRIL + "&granularity=MONTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].fiscalYear").doesNotExist())
                    .andExpect(jsonPath("$.points[0].quarter").doesNotExist());
        }

        @Test
        @DisplayName("AC-200 出荷基準を受け取る")
        void acceptsShipmentBasis() throws Exception {
            // 操作と確認：出荷していない注文は数えない
            mockMvc.perform(get(REPORTS + "/sales-trend?" + APRIL + "&granularity=MONTH&basis=SHIPMENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].amount").value(0));
        }

        @Test
        @DisplayName("BR-26 消費税と送料を除く指定を受け取る")
        void acceptsExclusions() throws Exception {
            // 操作と確認：明細の税抜小計 2,000 円で、送料を除いた印
            mockMvc.perform(get(REPORTS + "/sales-trend?" + APRIL + "&granularity=MONTH&excludeTax=true&excludeShipping=true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].amount").value(lineExclTax(2)))
                    .andExpect(jsonPath("$.shippingExcluded").value(true));
        }

        @Test
        @DisplayName("AC-199 商品の選択を受け取る")
        void acceptsProduct() throws Exception {
            // 操作と確認：T シャツの明細の税込小計 2,200 円で、送料を除いた印
            mockMvc.perform(get(REPORTS + "/sales-trend?" + APRIL + "&granularity=MONTH&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points[0].amount").value(lineInclTax(2)))
                    .andExpect(jsonPath("$.shippingExcluded").value(true));
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "WEEK, 27",
                "MONTH, 6",
                "QUARTER, 2",
                "YEAR, 1",
        })
        @DisplayName("AC-198 同じ期間を粒度ごとの行で取得でき、行の合計は粒度によらず同じ")
        void rowsPerGranularity(String granularity, int rows) throws Exception {
            // 操作：上半期を、粒度で区切って取得する
            var body = mockMvc.perform(get(REPORTS + "/sales-trend?" + FIRST_HALF + "&granularity=" + granularity))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points", hasSize(rows)))
                    .andReturn().getResponse().getContentAsString();

            // 確認：行の売上額と販売数量の合計が、注文の 3,200 円と 2 枚
            List<Number> amounts = JsonPath.read(body, "$.points[*].amount");
            List<Number> quantities = JsonPath.read(body, "$.points[*].quantity");
            assertThat(amounts.stream().mapToLong(Number::longValue).sum()).isEqualTo(total(2));
            assertThat(quantities.stream().mapToLong(Number::longValue).sum()).isEqualTo(2);
        }

        @Test
        @DisplayName("BR-24 期間が 5 年を超えると 400")
        void periodTooLong() throws Exception {
            // 操作と確認：期間の誤りと、5 年以内にする案内
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-01&to=2036-04-01&granularity=YEAR"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PERIOD"))
                    .andExpect(jsonPath("$.message").value("期間は、始まりの日から終わりの日までを 5 年以内にしてください。"));
        }

        @Test
        @DisplayName("BR-24 始まりが終わりより後なら 400")
        void periodReversed() throws Exception {
            // 操作と確認：期間の誤り
            mockMvc.perform(get(REPORTS + "/sales-trend?from=2031-04-02&to=2031-04-01&granularity=MONTH"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PERIOD"));
        }

        @Test
        @DisplayName("粒度がなければ 400")
        void granularityRequired() throws Exception {
            // 操作と確認：入力の誤り
            mockMvc.perform(get(REPORTS + "/sales-trend?" + APRIL))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("バリアントの売れ行き")
    class Variants {

        @Test
        @DisplayName("AC-202 選んだ商品の ID と商品名を返す")
        void product() throws Exception {
            // 操作と確認：T シャツの ID と商品名
            mockMvc.perform(get(REPORTS + "/variant-sales?" + APRIL + "&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(shirt.id.toString()))
                    .andExpect(jsonPath("$.productName").value(PRODUCT_NAME));
        }

        @Test
        @DisplayName("AC-202、203、204 バリアントごとの品番・軸の値・販売数量・売上額を返し、売れなかったバリアントは 0")
        void variants() throws Exception {
            // 操作と確認：売れた白（2 枚、2,200 円）、売れなかった黒（0）の順
            mockMvc.perform(get(REPORTS + "/variant-sales?" + APRIL + "&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.variants[*].variantId").value(contains(
                            shirt.variantIds.getFirst().toString(), shirt.variantIds.get(1).toString())))
                    .andExpect(jsonPath("$.variants[0].variantCode").value(shirt.variantCodes.getFirst()))
                    .andExpect(jsonPath("$.variants[0].axisValues[0].name").value("色"))
                    .andExpect(jsonPath("$.variants[0].axisValues[0].value").value("白"))
                    .andExpect(jsonPath("$.variants[0].quantity").value(2))
                    .andExpect(jsonPath("$.variants[0].amount").value(lineInclTax(2)))
                    .andExpect(jsonPath("$.variants[1].quantity").value(0));
        }

        @Test
        @DisplayName("BR-26 バリアントの売れ行きには、送料を除いた印を付ける")
        void shippingExcluded() throws Exception {
            // 操作と確認：送料を除いた印
            mockMvc.perform(get(REPORTS + "/variant-sales?" + APRIL + "&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingExcluded").value(true));
        }

        @Test
        @DisplayName("商品がなければ 404")
        void unknownProduct() throws Exception {
            // 操作と確認：見つからない
            mockMvc.perform(get(REPORTS + "/variant-sales?" + APRIL + "&productId=" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("商品を指定しなければ 400")
        void productRequired() throws Exception {
            // 操作と確認：入力の誤り（商品は必須）
            mockMvc.perform(get(REPORTS + "/variant-sales?" + APRIL))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("地域・年齢層・性別")
    class Customers {

        @Test
        @DisplayName("AC-205、220、262 都道府県は、売れたものだけを、地域の名前を添えて返し、コードのない注文は名前なしで最後")
        void prefectures() throws Exception {
            // 準備：都道府県のない注文を追加する
            insertOrderWithoutBuyerAttributes();

            // 操作と確認：東京都（3,200 円、1 件）と、コードも名前もない不明の 2 行
            mockMvc.perform(get(REPORTS + "/customer-breakdown?" + APRIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prefectures", hasSize(2)))
                    .andExpect(jsonPath("$.prefectures[0].prefectureCode").value("13"))
                    .andExpect(jsonPath("$.prefectures[0].prefectureName").value("東京都"))
                    .andExpect(jsonPath("$.prefectures[0].amount").value(total(2)))
                    .andExpect(jsonPath("$.prefectures[0].orders").value(1))
                    .andExpect(jsonPath("$.prefectures[1].prefectureCode").doesNotExist())
                    .andExpect(jsonPath("$.prefectures[1].prefectureName").doesNotExist());
        }

        @Test
        @DisplayName("AC-206、208 年齢層は、全ての層を返し、年齢のない注文は不明に数える")
        void ageGroups() throws Exception {
            // 準備：年齢のない注文を追加する
            insertOrderWithoutBuyerAttributes();

            // 操作と確認：全ての層が若い順。30 代に 3,200 円の 1 件、不明に 2,100 円（税込 1,100 円 ＋ 送料 1,000 円）の 1 件
            mockMvc.perform(get(REPORTS + "/customer-breakdown?" + APRIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ageGroups[*].ageGroup").value(contains(
                            "TEENS_OR_YOUNGER", "TWENTIES", "THIRTIES", "FORTIES", "FIFTIES", "SIXTIES_OR_OLDER", "UNKNOWN")))
                    .andExpect(jsonPath("$.ageGroups[2].amount").value(total(2)))
                    .andExpect(jsonPath("$.ageGroups[2].orders").value(1))
                    .andExpect(jsonPath("$.ageGroups[6].ageGroup").value("UNKNOWN"))
                    .andExpect(jsonPath("$.ageGroups[6].amount").value(total(1)))
                    .andExpect(jsonPath("$.ageGroups[6].orders").value(1));
        }

        @Test
        @DisplayName("AC-219 性別は、全ての区分を返し、性別のない注文は不明に数える")
        void genders() throws Exception {
            // 準備：性別のない注文を追加する
            insertOrderWithoutBuyerAttributes();

            // 操作と確認：男性・女性・その他・不明の順。女性に 1 件、不明に 1 件
            mockMvc.perform(get(REPORTS + "/customer-breakdown?" + APRIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.genders[*].gender").value(contains("MALE", "FEMALE", "OTHER", "UNKNOWN")))
                    .andExpect(jsonPath("$.genders[1].orders").value(1))
                    .andExpect(jsonPath("$.genders[3].orders").value(1));
        }

        @Test
        @DisplayName("BR-26 商品を指定しなければ、送料を除いた印を付けない")
        void includesShippingWithoutProduct() throws Exception {
            // 操作と確認：送料を含めた売上額
            mockMvc.perform(get(REPORTS + "/customer-breakdown?" + APRIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingExcluded").value(false));
        }

        @Test
        @DisplayName("AC-207 商品を指定すると、その商品を含む注文だけを数え、売上額はその商品の明細の分だけ")
        void breakdownByProduct() throws Exception {
            // 準備：靴下だけの、40 歳の男性の注文を追加する
            insertSocksOrder();

            // 確認（前提）：商品を指定しなければ、30 代と 40 代に 1 件ずつ
            mockMvc.perform(get(REPORTS + "/customer-breakdown?" + APRIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ageGroups[2].orders").value(1))
                    .andExpect(jsonPath("$.ageGroups[3].orders").value(1))
                    .andExpect(jsonPath("$.shippingExcluded").value(false));

            // 操作と確認：T シャツを指定すると、T シャツの注文の明細 2,200 円だけで、靴下の 40 代・男性は 0
            mockMvc.perform(get(REPORTS + "/customer-breakdown?" + APRIL + "&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prefectures", hasSize(1)))
                    .andExpect(jsonPath("$.prefectures[0].amount").value(lineInclTax(2)))
                    .andExpect(jsonPath("$.prefectures[0].orders").value(1))
                    .andExpect(jsonPath("$.ageGroups[2].amount").value(lineInclTax(2)))
                    .andExpect(jsonPath("$.ageGroups[2].orders").value(1))
                    .andExpect(jsonPath("$.ageGroups[3].amount").value(0))
                    .andExpect(jsonPath("$.ageGroups[3].orders").value(0))
                    .andExpect(jsonPath("$.genders[1].orders").value(1))
                    .andExpect(jsonPath("$.genders[0].orders").value(0));
        }

        @Test
        @DisplayName("AC-207、BR-26 商品を指定すると、送料を除いた印を付ける")
        void marksShippingExcludedForProduct() throws Exception {
            // 準備：靴下だけの注文を追加する
            insertSocksOrder();

            // 操作と確認：送料を除いた印
            mockMvc.perform(get(REPORTS + "/customer-breakdown?" + APRIL + "&productId=" + shirt.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingExcluded").value(true));
        }

        /**
         * 都道府県・年齢・性別のない、T シャツ（黒）1 枚の注文の写しを入れる。
         */
        private void insertOrderWithoutBuyerAttributes() {
            new ReportTestData(dsl).order("ACCEPTED", "2031-04-06").prefecture(null)
                    .line(shirt.id, shirt.variantIds.get(1), 1).insert();
        }

        /**
         * 靴下を商品カタログに作り、靴下だけの、40 歳の男性の注文の写しを入れる。
         *
         * @throws Exception 商品カタログの API の呼び出しに失敗したとき
         */
        private void insertSocksOrder() throws Exception {
            var socks = catalog.product(taxCategoryId, "靴下", 500, "色").variant("白");
            new ReportTestData(dsl).order("ACCEPTED", "2031-04-07").buyer(40, "MALE")
                    .line(socks.id, socks.variantIds.getFirst(), 1).insert();
        }
    }

    @Test
    @DisplayName("NFR-16 レポートの応答の型は、集計の値だけを持ち、会員を特定できる項目を持たない")
    void responsesHaveNoPersonalData() {
        // 確認：応答の全ての型の項目が、集計の値と区分だけ
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

    // ============================== 確認の補助 ==============================

    /**
     * 応答の型の項目（static でないフィールド）の名前を返す。
     *
     * @param type 応答の型
     * @return 項目の名前
     */
    private static String[] fields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .toArray(String[]::new);
    }
}
