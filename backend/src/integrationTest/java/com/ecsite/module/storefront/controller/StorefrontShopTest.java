package com.ecsite.module.storefront.controller;

import com.ecsite.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗の情報の受け入れテスト（T2804、AC-96）。問い合わせ先は、統合テストの設定の値を返す。
 */
@IntegrationTest
@AutoConfigureMockMvc
class StorefrontShopTest {

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("AC-96 店舗の情報として、設定した問い合わせ先を返す")
    void returnsContactEmail() throws Exception {
        mockMvc.perform(get("/api/store/shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("contact@shop.example"));
    }
}
