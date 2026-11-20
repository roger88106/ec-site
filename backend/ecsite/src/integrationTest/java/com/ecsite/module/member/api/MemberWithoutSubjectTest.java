package com.ecsite.module.member.api;

import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.ecsite.support.http.ProblemAssertions.problem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IdP のアカウントの ID（{@code sub}）のない会員の token の統合テスト
 *
 * <p>会員 ID を求める処理で失敗し、想定外のエラー（500）になる今の振る舞いを固定する。
 * 会員 ID を求める操作は、店舗 API の会員向けの操作が使う。ここでは、ログインが必要な操作（カートの取得）と、
 * ログインが任意の操作（カートに入れる）を 1 つずつ確認する。
 */
@IntegrationTest
class MemberWithoutSubjectTest {

    // ============================== 定数 ==============================

    /**
     * 想定外のエラーのメッセージ
     */
    private static final String SYSTEM_ERROR = "予期しないエラーが発生しました。";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    // ============================== テスト ==============================

    @Test
    @DisplayName("ログインが必要な操作は、subject のない token で 500（想定外のエラー）になる")
    void requiredOperationFails() throws Exception {
        // 操作と確認：会員のカートの取得は、会員 ID を求める処理で失敗する
        mockMvc.perform(get("/api/store/me/cart").with(TestTokens.member().withoutSubject().bearer()))
                .andExpect(status().isInternalServerError())
                .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
    }

    @Test
    @DisplayName("ログインが任意の操作も、subject のない token で 500（想定外のエラー）になる")
    void optionalOperationFails() throws Exception {
        // 操作と確認：カートに入れる操作は、会員 ID を求める処理で失敗する
        mockMvc.perform(post("/api/store/carts/" + UUID.randomUUID() + "/items")
                        .with(TestTokens.member().withoutSubject().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
                .andExpect(status().isInternalServerError())
                .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
    }
}
