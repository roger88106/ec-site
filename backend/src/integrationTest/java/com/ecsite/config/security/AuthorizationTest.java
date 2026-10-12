package com.ecsite.config.security;

import com.ecsite.IntegrationTest;
import com.ecsite.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証と認可の統合テスト（T3203、plan 003 第 3.1〜3.3 節）。<br />
 * token は {@link TestTokens} がテストの鍵で署名したもので、本番と同じ検証（署名・issuer・audience・有効期限）と、
 * {@code admin.yaml} の {@code x-required-permission} による判定を通す。<br />
 * 管理 API の要求には既定で管理者の token が付く（{@link com.ecsite.TestSecurityConfig}）ので、token なしは {@link TestTokens#none()} で表す。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AuthorizationTest {

    private static final String TAX_CATEGORIES = "/api/admin/tax-categories";

    // 期待するメッセージ（messages.properties。plan 003 第 3.3 節）
    private static final String UNAUTHENTICATED = "ログインしてください。";
    private static final String FORBIDDEN = "この操作を行う権限がありません。";

    @Autowired
    MockMvc mockMvc;

    @Nested
    @DisplayName("AC-137 管理 API の認証")
    class Authentication {

        @Test
        @DisplayName("AC-137 token がないと 401")
        void rejectsMissingToken() throws Exception {
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.none()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-137 有効期限の切れた token は 401")
        void rejectsExpiredToken() throws Exception {
            var token = TestTokens.administrator().expiresAt(Instant.now().minusSeconds(120));

            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-137 署名の不正な token は 401")
        void rejectsTokenWithInvalidSignature() throws Exception {
            var token = TestTokens.administrator().signedByUnknownKey();

            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-137 token の形でない値は 401")
        void rejectsMalformedToken() throws Exception {
            mockMvc.perform(get(TAX_CATEGORIES).header("Authorization", "Bearer not-a-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-138 会員の token（消費者の realm）は 401")
        void rejectsMemberToken() throws Exception {
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.member().bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-138 運営者の realm の鍵で署名していても、issuer が違えば 401")
        void rejectsTokenFromOtherIssuer() throws Exception {
            var token = TestTokens.administrator().issuer(TestTokens.STORE_ISSUER);

            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }

        @Test
        @DisplayName("AC-138 audience が管理 API でない token は 401")
        void rejectsTokenForOtherAudience() throws Exception {
            var token = TestTokens.administrator().audience("ec-admin-web");

            mockMvc.perform(get(TAX_CATEGORIES).with(token.bearer()))
                    .andExpect(status().isUnauthorized())
                    .andExpectAll(problem("UNAUTHENTICATED", UNAUTHENTICATED));
        }
    }

    @Nested
    @DisplayName("AC-136 管理 API の権限")
    class Permission {

        @Test
        @DisplayName("AC-136 操作の権限があれば使える")
        void allowsWithPermission() throws Exception {
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.operator("tax:read").bearer()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-136 パスに変数のある操作も、権限があれば使える（見つからなければ 404）")
        void allowsPathWithVariable() throws Exception {
            mockMvc.perform(get(TAX_CATEGORIES + "/" + UUID.randomUUID()).with(TestTokens.operator("tax:read").bearer()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-136 ほかの資源の権限だけでは 403")
        void forbidsWithPermissionOfOtherResource() throws Exception {
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.operator("product:read", "product:write").bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("AC-136 参照の権限だけでは、更新は 403")
        void forbidsWriteWithReadPermission() throws Exception {
            mockMvc.perform(post(TAX_CATEGORIES).with(TestTokens.operator("tax:read").bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"標準\"}"))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("AC-136 権限のない運営者の token は 403")
        void forbidsWithoutPermission() throws Exception {
            mockMvc.perform(get(TAX_CATEGORIES).with(TestTokens.operator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }
    }

    @Nested
    @DisplayName("NFR-3 admin.yaml にない要求は拒否する")
    class DefaultDeny {

        @Test
        @DisplayName("NFR-3 admin.yaml にないメソッドは、すべての権限があっても 403")
        void forbidsUndeclaredMethod() throws Exception {
            mockMvc.perform(delete(TAX_CATEGORIES).with(TestTokens.administrator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("NFR-3 admin.yaml にないパスは、すべての権限があっても 403")
        void forbidsUndeclaredPath() throws Exception {
            mockMvc.perform(get("/api/admin/undeclared").with(TestTokens.administrator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("NFR-3 admin.yaml にない PATCH は、すべての権限があっても 403")
        void forbidsUndeclaredPatch() throws Exception {
            mockMvc.perform(patch(TAX_CATEGORIES + "/" + UUID.randomUUID()).with(TestTokens.administrator().bearer()))
                    .andExpect(status().isForbidden())
                    .andExpectAll(problem("FORBIDDEN", FORBIDDEN));
        }

        @Test
        @DisplayName("NFR-3 admin.yaml にない要求も、token がなければ 401")
        void rejectsUndeclaredWithoutToken() throws Exception {
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
            mockMvc.perform(get("/api/store/products").with(TestTokens.none()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("店舗 API のカートの作成は、token なしで使える")
        void storeCartWithoutToken() throws Exception {
            mockMvc.perform(post("/api/store/carts").with(TestTokens.none()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ヘルスチェックは token なしで使える")
        void healthWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }

    // ============================== 検証の部品 ==============================

    /**
     * Problem Details の形（constitution 9）。
     *
     * @param errorCode エラーコード
     * @param message   メッセージ
     * @return 検証
     */
    private static ResultMatcher[] problem(String errorCode, String message) {
        return List.of(
                content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.errorCode").value(errorCode),
                jsonPath("$.message").value(message),
                jsonPath("$.traceId").isNotEmpty()
        ).toArray(ResultMatcher[]::new);
    }
}
