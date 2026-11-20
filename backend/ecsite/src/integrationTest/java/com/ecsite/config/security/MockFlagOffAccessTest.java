package com.ecsite.config.security;

import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import static com.ecsite.support.http.ProblemAssertions.problem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * mock のフラグを立てない設定の、店舗 API・管理 API 以外の URL の認可の検査
 *
 * <p>決済と物流の mock のフラグを、どちらも立てない（本番と同じ）。決済は Stripe を使うので、Stripe の鍵に試験用の値を渡す
 * （このクラスのテストは Stripe に接続しない）。フラグを立てた設定の確認は {@code BusinessPortAccessTest}。
 */
@IntegrationTest
@TestPropertySource(properties = {
        "ecsite.payment.mock.enabled=false",
        "ecsite.shipping.mock.enabled=false",
        "ecsite.payment.stripe.secret-key=sk_test_dummy",
        "ecsite.payment.stripe.webhook-secret=whsec_test_dummy"})
class MockFlagOffAccessTest {

    // ============================== 定数 ==============================

    /**
     * 「ログインが必要」のメッセージ（messages.properties）。メッセージを変えたら、ここも合わせる
     */
    private static final String UNAUTHENTICATED = "ログインしてください。";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    // ============================== テスト ==============================

    /**
     * mock の画面のリクエストの、場合の名前とリクエストを返す。
     *
     * @return 見本
     */
    static Stream<Arguments> mockPageRequests() {
        return Stream.of(
                Arguments.of("決済の支払画面を開く", get("/mock-payment/mock_page_unknown")),
                Arguments.of("決済の支払画面で支払う", post("/mock-payment/mock_page_unknown/pay")),
                Arguments.of("配送業者の画面を開く", get("/mock-carrier")),
                Arguments.of("配送業者の画面で通知を送る", post("/mock-carrier/orders/" + UUID.randomUUID() + "/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("status", "RETURNED")));
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("mockPageRequests")
    @DisplayName("DC-477-3 mock のフラグを立てていないモジュールの mock の画面のパスは、401（Problem Details）で拒む")
    void rejectsMockPage(String name, MockHttpServletRequestBuilder request) throws Exception {
        // 操作と確認：認証なしで開くと、見つからない（404）でなく、「ログインが必要」の Problem Details
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
    }

    @Test
    @DisplayName("DC-477-5 決済の通知のパスは、認証なしで口の中まで届く")
    void acceptsStripeWebhook() throws Exception {
        // 操作と確認：署名なしで送ると、口の中の署名の確認まで届き、本文のない 400 が返る
        // （認可で拒まれた応答は、Problem Details の本文を持つ）
        mockMvc.perform(post("/api/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }
}
