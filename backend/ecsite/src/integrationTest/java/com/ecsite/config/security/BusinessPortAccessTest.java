package com.ecsite.config.security;

import com.ecsite.IntegrationTest;
import com.ecsite.module.shipping.infrastructure.carrier.CarrierSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗 API・管理 API 以外の URL の認可の検査
 *
 * <p>既定で拒み、決めた口（通知の口、mock の口、運用の口）だけを許す。統合テストの設定は mock フラグを立てている。
 */
@IntegrationTest
class BusinessPortAccessTest {

    // ============================== 定数 ==============================

    /**
     * 統合テストの設定の、物流サービスの通知の署名の鍵
     */
    private static final String CARRIER_WEBHOOK_SECRET = "test-carrier-webhook-secret";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-235 決めていない URL は、401（Problem Details）で拒む")
    void rejectsUndecidedUrl() throws Exception {
        // 操作と確認：決めていない URL を開くと、「ログインが必要」の Problem Details
        mockMvc.perform(get("/undecided"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("AC-235 物流の通知の口は、認証なしで口の中まで届く")
    void acceptsCarrierWebhook() throws Exception {
        // 準備：正しい署名を付けた、中身のない通知
        var body = "{}";
        var signature = new CarrierSignature(CARRIER_WEBHOOK_SECRET).sign(body.getBytes(StandardCharsets.UTF_8));

        // 操作と確認：認証なしで送ると、口の中の契約の検証まで届き、400 が返る
        mockMvc.perform(post("/api/webhooks/carrier").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(CarrierSignature.HEADER, signature))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("AC-235 mock の配送業者の画面は、フラグを立てていれば認証なしで開ける")
    void opensMockCarrierPage() throws Exception {
        // 操作と確認：統合テストの設定（フラグを立てている）で、認証なしで開ける
        mockMvc.perform(get("/mock-carrier"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-235 許した口の中の 404 は、404 のまま返る")
    void keepsNotFoundInAllowedUrl() throws Exception {
        // 操作と確認：mock の支払画面の口で、ない画面を開くと 404
        mockMvc.perform(get("/mock-payment/mock_page_unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-235 許した口にない URL は、404 でなく 401 で拒む")
    void rejectsUnknownUnderApi() throws Exception {
        // 操作と確認：/api の下の、店舗 API でも管理 API でもない URL を開くと 401
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized());
    }
}
