package com.ecsite.config.security;

import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.stream.Stream;

import static com.ecsite.support.http.ProblemAssertions.problem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 解釈が分かれうるリクエストの拒否の検査
 *
 * <p>パスやメソッドの書き方を変えたリクエストが、契約の操作として許されず、拒まれることを確認する。
 *
 * <p>管理 API のリクエストには既定で管理者（全ての権限）の token が、店舗 API のリクエストには既定の会員の token が付く
 * （{@code TestSecurityConfig}）。
 */
@IntegrationTest
@Transactional
class AmbiguousRequestTest {

    // ============================== 定数 ==============================

    /**
     * 税区分の一覧の管理 API のパス
     */
    private static final String TAX_CATEGORIES = "/api/admin/tax-categories";

    /**
     * 商品の一覧の店舗 API のパス（token が不要な操作）
     */
    private static final String STORE_PRODUCTS = "/api/store/products";

    /**
     * 会員のカートの店舗 API のパス（会員の token が必要な操作）
     */
    private static final String MEMBER_CART = "/api/store/me/cart";

    /**
     * 「ログインが必要」のメッセージ（messages.properties）
     */
    private static final String UNAUTHENTICATED = "ログインしてください。";

    /**
     * 「権限がない」のメッセージ（messages.properties）
     */
    private static final String FORBIDDEN = "この操作を行う権限がありません。";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("末尾のスラッシュ")
    class TrailingSlash {

        @Test
        @DisplayName("DC-477-7 管理 API の操作のパスの末尾にスラッシュを付けると、全ての権限があっても 403")
        void forbidsAdminOperation() throws Exception {
            // 操作と確認：管理者の token で、税区分の一覧のパスの末尾にスラッシュを付けて呼ぶと、「権限がない」
            mockMvc.perform(get(TAX_CATEGORIES + "/"))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("DC-477-7 token が不要な店舗 API の操作のパスの末尾にスラッシュを付けると、token なしで 401")
        void rejectsPublicStoreOperationWithoutToken() throws Exception {
            // 操作と確認：token なしで、商品の一覧のパスの末尾にスラッシュを付けて呼ぶと、「ログインが必要」
            mockMvc.perform(get(STORE_PRODUCTS + "/").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("DC-477-7 会員向けの店舗 API の操作のパスの末尾にスラッシュを付けると、会員の token があっても 403")
        void forbidsMemberStoreOperation() throws Exception {
            // 操作と確認：会員の token で、カートのパスの末尾にスラッシュを付けて呼ぶと、「権限がない」
            mockMvc.perform(get(MEMBER_CART + "/"))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }
    }

    @Nested
    @DisplayName("符号化した文字と区切り")
    class EncodedCharacters {

        /**
         * 符号化した点・スラッシュと、セミコロンを含むパスを返す。
         *
         * @return 場合の名前とパス（符号化したまま送る）
         */
        static Stream<Arguments> ambiguousPaths() {
            return Stream.of(
                    Arguments.of("管理 API の %2e", "/api/admin/%2e%2e/admin/tax-categories"),
                    Arguments.of("管理 API の %2F", "/api/admin/tax-categories%2F"),
                    Arguments.of("管理 API の ;", "/api/admin/tax-categories;x=1"),
                    Arguments.of("店舗 API の %2e", "/api/store/products/%2e%2e/me/cart"),
                    Arguments.of("店舗 API の %2F", "/api/store/me%2Fcart"),
                    Arguments.of("店舗 API の ;", "/api/store/me;x=1/cart"));
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("ambiguousPaths")
        @DisplayName("DC-477-7 符号化した点・スラッシュとセミコロンを含むパスは、token があっても、操作に届く前に 400 で拒む")
        void rejectsBeforeReachingOperation(String name, String path) throws Exception {
            // 操作と確認：既定の token を付けて、符号化したままのパスで呼ぶと、400 で、操作の応答の本文がない
            mockMvc.perform(get(URI.create(path)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    @DisplayName("契約にないメソッド")
    class UndeclaredMethod {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"HEAD", "OPTIONS"})
        @DisplayName("DC-477-7 管理 API の操作のパスに契約にないメソッドで送ると、全ての権限があっても 403")
        void forbidsAdminOperation(String method) throws Exception {
            // 操作と確認：管理者の token で、税区分の一覧のパスに送ると、「権限がない」
            mockMvc.perform(request(HttpMethod.valueOf(method), TAX_CATEGORIES))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"HEAD", "OPTIONS"})
        @DisplayName("DC-477-7 token が不要な店舗 API の操作のパスに契約にないメソッドで送ると、token なしで 401")
        void rejectsPublicStoreOperationWithoutToken(String method) throws Exception {
            // 操作と確認：token なしで、商品の一覧のパスに送ると、「ログインが必要」
            mockMvc.perform(request(HttpMethod.valueOf(method), STORE_PRODUCTS).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }

    @Nested
    @DisplayName("大文字のパス")
    class UpperCase {

        @Test
        @DisplayName("DC-477-7 管理 API の操作のパスを大文字にすると、全ての権限があっても 403")
        void forbidsAdminOperation() throws Exception {
            // 操作と確認：管理者の token で、税区分の一覧のパスの操作の部分を大文字にして呼ぶと、「権限がない」
            mockMvc.perform(get("/api/admin/TAX-CATEGORIES"))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("DC-477-7 管理 API の接頭辞から大文字にすると、管理者の token があっても 401")
        void rejectsAdminPrefix() throws Exception {
            // 操作と確認：管理者の token で、全体を大文字にした税区分の一覧のパスを呼ぶと、「ログインが必要」
            mockMvc.perform(get("/API/ADMIN/TAX-CATEGORIES").with(TestTokens.administrator().bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("DC-477-7 token が不要な店舗 API の操作のパスを大文字にすると、token なしで 401")
        void rejectsPublicStoreOperationWithoutToken() throws Exception {
            // 操作と確認：token なしで、商品の一覧のパスの操作の部分を大文字にして呼ぶと、「ログインが必要」
            mockMvc.perform(get("/api/store/PRODUCTS").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("DC-477-7 店舗 API の接頭辞から大文字にすると、会員の token があっても 401")
        void rejectsStorePrefix() throws Exception {
            // 操作と確認：会員の token で、全体を大文字にしたカートのパスを呼ぶと、「ログインが必要」
            mockMvc.perform(get("/API/STORE/ME/CART").with(TestTokens.member().bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }

    @Nested
    @DisplayName("API の接頭辞そのもの")
    class Prefix {

        @Test
        @DisplayName("DC-477-7 管理 API の接頭辞そのものは、全ての権限があっても 403")
        void forbidsAdminPrefix() throws Exception {
            // 操作と確認：管理者の token で、管理 API の接頭辞を呼ぶと、「権限がない」
            mockMvc.perform(get("/api/admin").with(TestTokens.administrator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("DC-477-7 店舗 API の接頭辞そのものは、token なしで 401")
        void rejectsStorePrefixWithoutToken() throws Exception {
            // 操作と確認：token なしで、店舗 API の接頭辞を呼ぶと、「ログインが必要」
            mockMvc.perform(get("/api/store").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }

    @Nested
    @DisplayName("エラーのパス")
    class ErrorPath {

        @Test
        @DisplayName("DC-477-7 エラーのパスへの直接のリクエストは、管理者の token があっても 401")
        void rejectsDirectRequest() throws Exception {
            // 操作と確認：管理者の token で、エラーのパスを直接呼ぶと、「ログインが必要」
            mockMvc.perform(get("/error").with(TestTokens.administrator().bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }
}
