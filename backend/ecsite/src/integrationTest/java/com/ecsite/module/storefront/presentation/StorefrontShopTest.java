package com.ecsite.module.storefront.presentation;

import com.ecsite.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗の情報の受け入れテスト
 *
 * <p>店舗の情報の API が、設定した問い合わせ先を返すことを確認する。問い合わせ先は、統合テストの設定の値を返す。
 */
@IntegrationTest
class StorefrontShopTest {

    // ============================== 定数 ==============================

    /**
     * 統合テストの設定にある問い合わせ先
     */
    private static final String CONTACT_EMAIL = "contact@shop.example";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-96 店舗の情報として、設定した問い合わせ先を返す")
    void returnsContactEmail() throws Exception {
        // 操作と確認：設定の問い合わせ先
        mockMvc.perform(get("/api/store/shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value(CONTACT_EMAIL));
    }
}
