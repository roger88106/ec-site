package com.ecsite.config.security;

import com.ecsite.IntegrationTest;
import com.ecsite.TestSecurityConfig;
import com.ecsite.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.ProblemAssertions.problem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証と認可の統合テスト
 *
 * <p>token は {@link TestTokens} がテストの鍵で署名したもので、本番と同じ検証（署名・issuer・audience・有効期限）と、
 * {@code admin.yaml} の {@code x-required-permission} による判定を通す。
 *
 * <p>管理 API のリクエストには既定で管理者の token が付く（{@link TestSecurityConfig}）ので、token なしは {@link TestTokens#none()} で表す。
 */
@IntegrationTest
@Transactional
class AuthorizationTest {

    // ============================== 定数 ==============================

    /**
     * 税区分の管理 API のパス
     */
    private static final String TAX_CATEGORIES = "/api/admin/tax-categories";

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
    @DisplayName("管理 API の認証")
    class Authentication {

        @Test
        @DisplayName("AC-137 token がないと 401")
        void rejectsMissingToken() throws Exception {
            // 操作と確認：token なしで呼ぶと、「ログインが必要」
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-137 有効期限の切れた token は 401")
        void rejectsExpiredToken() throws Exception {
            // 準備：2 分前に期限の切れた管理者の token
            var token = TestTokens.administrator().expiresAt(Instant.now().minusSeconds(120));

            // 操作と確認：「ログインが必要」
            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-137 署名の不正な token は 401")
        void rejectsTokenWithInvalidSignature() throws Exception {
            // 準備：知らない鍵で署名した管理者の token
            var token = TestTokens.administrator().signedByUnknownKey();

            // 操作と確認：「ログインが必要」
            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-137 token の形でない値は 401")
        void rejectsMalformedToken() throws Exception {
            // 操作と確認：token の形でない値で呼ぶと、「ログインが必要」
            mockMvc.perform(get(TAX_CATEGORIES).header("Authorization", "Bearer not-a-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-138 会員の token（消費者の realm）は 401")
        void rejectsMemberToken() throws Exception {
            // 操作と確認：会員の token で呼ぶと、「ログインが必要」
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.member().bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-138 運営者の realm の鍵で署名していても、issuer が違えば 401")
        void rejectsTokenFromOtherIssuer() throws Exception {
            // 準備：issuer を店舗の realm にした管理者の token
            var token = TestTokens.administrator().issuer(TestTokens.STORE_ISSUER);

            // 操作と確認：「ログインが必要」
            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-138 audience が管理 API でない token は 401")
        void rejectsTokenForOtherAudience() throws Exception {
            // 準備：audience を管理画面にした管理者の token
            var token = TestTokens.administrator().audience("ec-admin-web");

            // 操作と確認：「ログインが必要」
            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }

    @Nested
    @DisplayName("店舗 API の会員向けの操作の認証")
    class StoreAuthentication {

        @Test
        @DisplayName("AC-137 店舗 API の会員向けの操作は、有効期限の切れた token で 401")
        void rejectsExpiredMemberToken() throws Exception {
            // 準備：2 分前に期限の切れた会員の token
            var token = TestTokens.member().expiresAt(Instant.now().minusSeconds(120));

            // 操作と確認：「ログインが必要」
            mockMvc.perform(get(MEMBER_CART).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-137 店舗 API の会員向けの操作は、署名の不正な token で 401")
        void rejectsMemberTokenWithInvalidSignature() throws Exception {
            // 準備：知らない鍵で署名した会員の token
            var token = TestTokens.member().signedByUnknownKey();

            // 操作と確認：「ログインが必要」
            mockMvc.perform(get(MEMBER_CART).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-138 運営者の token では、店舗 API の会員向けの操作は 401")
        void rejectsOperatorToken() throws Exception {
            // 操作と確認：管理者の token で呼ぶと、「ログインが必要」
            mockMvc.perform(get(MEMBER_CART).with(TestTokens.administrator().bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }

    @Nested
    @DisplayName("管理 API の権限")
    class Permission {

        @Test
        @DisplayName("AC-136 操作の権限があれば使える")
        void allowsWithPermission() throws Exception {
            // 操作と確認：税区分の参照の権限で一覧を呼ぶと、使える
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.operator("tax:read").bearer()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-136 パスに変数のある操作も、権限があれば使える")
        void allowsPathWithVariable() throws Exception {
            // 操作と確認：税区分の参照の権限で、ない税区分を呼ぶと、認可を通って見つからない（404）
            mockMvc.perform(get(TAX_CATEGORIES + "/" + UUID.randomUUID()).with(TestTokens.operator("tax:read").bearer()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-136 ほかの資源の権限だけでは 403")
        void forbidsWithPermissionOfOtherResource() throws Exception {
            // 操作と確認：商品の権限だけで税区分を呼ぶと、「権限がない」
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.operator("product:read", "product:write").bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("AC-136 参照の権限だけでは、更新は 403")
        void forbidsWriteWithReadPermission() throws Exception {
            // 操作と確認：税区分の参照の権限で登録すると、「権限がない」
            mockMvc.perform(post(TAX_CATEGORIES).with(TestTokens.operator("tax:read").bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"標準\"}"))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("AC-136 権限のない運営者の token は 403")
        void forbidsWithoutPermission() throws Exception {
            // 操作と確認：権限のない運営者の token で呼ぶと、「権限がない」
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.operator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }
    }

    @Nested
    @DisplayName("admin.yaml にないリクエストの拒否")
    class DefaultDeny {

        @Test
        @DisplayName("NFR-3 admin.yaml にないメソッドは、すべての権限があっても 403")
        void forbidsUndeclaredMethod() throws Exception {
            // 操作と確認：管理者の token で、税区分の一覧を DELETE すると、「権限がない」
            mockMvc.perform(delete(TAX_CATEGORIES).with(TestTokens.administrator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("NFR-3 admin.yaml にないパスは、すべての権限があっても 403")
        void forbidsUndeclaredPath() throws Exception {
            // 操作と確認：管理者の token で、契約にないパスを呼ぶと、「権限がない」
            mockMvc.perform(get("/api/admin/undeclared").with(TestTokens.administrator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("NFR-3 admin.yaml にない PATCH は、すべての権限があっても 403")
        void forbidsUndeclaredPatch() throws Exception {
            // 操作と確認：管理者の token で、税区分を PATCH すると、「権限がない」
            mockMvc.perform(patch(TAX_CATEGORIES + "/" + UUID.randomUUID()).with(TestTokens.administrator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("NFR-3 admin.yaml にないリクエストも、token がなければ 401")
        void rejectsUndeclaredWithoutToken() throws Exception {
            // 操作と確認：token なしで、契約にないパスを呼ぶと、「ログインが必要」
            mockMvc.perform(get("/api/admin/undeclared").with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }

    @Nested
    @DisplayName("認証のない店舗 API と運用の口")
    class Public {

        @Test
        @DisplayName("店舗 API の商品の一覧は、token なしで使える")
        void storeProductsWithoutToken() throws Exception {
            // 操作と確認：token なしで使える
            mockMvc.perform(get("/api/store/products").with(TestTokens.none()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("店舗 API のカートの作成は、token なしで使える")
        void storeCartWithoutToken() throws Exception {
            // 操作と確認：token なしで作れる
            mockMvc.perform(post("/api/store/carts").with(TestTokens.none()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ヘルスチェックは token なしで使える")
        void healthWithoutToken() throws Exception {
            // 操作と確認：token なしで使える
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }
}
