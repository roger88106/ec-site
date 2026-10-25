package com.ecsite.config.security;

import com.ecsite.IntegrationTest;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗 API・管理 API 以外の URL の認可の検査。<br />
 * 既定で拒み、決めた口（通知の口、mock の口、運用の口）だけを許す。統合テストの設定は mock フラグを立てている。
 */
@IntegrationTest
@AutoConfigureMockMvc
class BusinessPortAccessTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("AC-235 決めていない URL は、401（Problem Details）で拒む")
    void rejectsUndecidedUrl() throws Exception {
        mockMvc.perform(get("/undecided"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("AC-235 物流の通知の口は、認証なしで口の中まで届く（署名が合えば、契約の応答 400 が返る）")
    void acceptsCarrierWebhook() throws Exception {
        var body = "{}";
        // 統合テストの設定の、物流サービスの通知の署名の鍵
        var signature = new CarrierSignature("test-carrier-webhook-secret").sign(body.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/api/webhooks/carrier").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(CarrierSignature.HEADER, signature))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("AC-235 mock の配送業者の画面は、フラグを立てていれば認証なしで開ける")
    void opensMockCarrierPage() throws Exception {
        mockMvc.perform(get("/mock-carrier"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-235 許した口の中の 404 は、404 のまま返る")
    void keepsNotFoundInAllowedUrl() throws Exception {
        mockMvc.perform(get("/mock-payment/mock_page_unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-235 許した口にない URL は、404 でなく 401 で拒む")
    void rejectsUnknownUnderApi() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized());
    }
}
