package com.ecsite.module.reporting.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * レポートの権限と、自分の権限の一覧と、レポートの設定の統合テスト
 *
 * <p>役割ごとの token は、運営者の realm の職務と同じ権限を持つ（{@link TestTokens}）。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class ReportPermissionApiTest {

    // ============================== 定数 ==============================

    /**
     * 期間のクエリ
     *
     * <p>2031 年 4 月。写しのない期間。
     */
    private static final String PERIOD = "from=2031-04-01&to=2031-04-30";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * バリアントの売れ行きで選ぶ商品
     */
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(Instant.parse("2026-10-07T15:00:00Z"));
        var catalog = new CatalogTestData(mockMvc, clock);
        productId = catalog.product(catalog.taxCategory("標準", "10"), "T シャツ", 1000, "色").variant("白").id;
    }

    /**
     * 役割と、売上の推移を見られるか
     *
     * @return 役割の名前、token、見られるか
     */
    static Stream<Arguments> salesTrendByRole() {
        return Stream.of(
                Arguments.of("管理者", (Supplier<TestTokens.Builder>) TestTokens::administrator, true),
                Arguments.of("管理職", (Supplier<TestTokens.Builder>) TestTokens::manager, true),
                Arguments.of("営業", (Supplier<TestTokens.Builder>) TestTokens::sales, false),
                Arguments.of("閲覧者", (Supplier<TestTokens.Builder>) TestTokens::viewer, false));
    }

    /**
     * 役割と、バリアントの売れ行きと地域・年齢層・性別を見られるか
     *
     * @return 役割の名前、token、見られるか
     */
    static Stream<Arguments> otherReportsByRole() {
        return Stream.of(
                Arguments.of("管理者", (Supplier<TestTokens.Builder>) TestTokens::administrator, true),
                Arguments.of("管理職", (Supplier<TestTokens.Builder>) TestTokens::manager, true),
                Arguments.of("営業", (Supplier<TestTokens.Builder>) TestTokens::sales, true),
                Arguments.of("閲覧者", (Supplier<TestTokens.Builder>) TestTokens::viewer, false));
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("レポートの権限")
    class Reports {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.module.reporting.presentation.ReportPermissionApiTest#salesTrendByRole")
        @DisplayName("AC-215、217、BR-28 売上の推移は、管理者と管理職だけが見られ、他の役割は 403")
        void salesTrend(String role, Supplier<TestTokens.Builder> token, boolean allowed) throws Exception {
            // 操作と確認：見られる役割は 200、見られない役割は 403
            mockMvc.perform(get("/api/admin/reports/sales-trend?" + PERIOD + "&granularity=MONTH").with(token.get().bearer()))
                    .andExpect(allowed ? status().isOk() : status().isForbidden());
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.module.reporting.presentation.ReportPermissionApiTest#otherReportsByRole")
        @DisplayName("AC-215、217、BR-28 バリアントの売れ行きは、管理者・管理職・営業が見られ、閲覧者は 403")
        void variantSales(String role, Supplier<TestTokens.Builder> token, boolean allowed) throws Exception {
            // 操作と確認：見られる役割は 200、見られない役割は 403
            mockMvc.perform(get("/api/admin/reports/variant-sales?" + PERIOD + "&productId=" + productId).with(token.get().bearer()))
                    .andExpect(allowed ? status().isOk() : status().isForbidden());
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.module.reporting.presentation.ReportPermissionApiTest#otherReportsByRole")
        @DisplayName("AC-215、217、BR-28 地域・年齢層・性別は、管理者・管理職・営業が見られ、閲覧者は 403")
        void customerBreakdown(String role, Supplier<TestTokens.Builder> token, boolean allowed) throws Exception {
            // 操作と確認：見られる役割は 200、見られない役割は 403
            mockMvc.perform(get("/api/admin/reports/customer-breakdown?" + PERIOD).with(token.get().bearer()))
                    .andExpect(allowed ? status().isOk() : status().isForbidden());
        }

        @Test
        @DisplayName("AC-215 token がないと、レポートは 401")
        void withoutToken() throws Exception {
            // 操作と確認：認証の誤り
            mockMvc.perform(get("/api/admin/reports/customer-breakdown?" + PERIOD).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("レポートの設定")
    class Settings {

        @Test
        @DisplayName("AC-197 レポートの権限のない閲覧者も、会計年度の始まりの月を受け取れる")
        void fiscalYearStartMonth() throws Exception {
            // 操作と確認：既定の 4 月
            mockMvc.perform(get("/api/admin/reports/settings").with(TestTokens.viewer().bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fiscalYearStartMonth").value(4));
        }

        @Test
        @DisplayName("token がないと 401")
        void withoutToken() throws Exception {
            // 操作と確認：認証の誤り
            mockMvc.perform(get("/api/admin/reports/settings").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("自分の権限の一覧")
    class Me {

        @Test
        @DisplayName("AC-214 token の権限を返す")
        void permissionsOfToken() throws Exception {
            // 操作と確認：営業の権限の全て
            mockMvc.perform(get("/api/admin/me").with(TestTokens.sales().bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissions", containsInAnyOrder(
                            "tax:read", "category:read", "product:read", "inventory:read", "order:read",
                            "report-variant-sales:read", "report-customer:read")));
        }

        @Test
        @DisplayName("AC-214 権限のない運営者も、空の一覧を受け取れる")
        void operatorWithoutPermission() throws Exception {
            // 操作と確認：空の一覧
            mockMvc.perform(get("/api/admin/me").with(TestTokens.operator().bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissions", empty()));
        }

        @Test
        @DisplayName("token がないと 401")
        void withoutToken() throws Exception {
            // 操作と確認：認証の誤り
            mockMvc.perform(get("/api/admin/me").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
