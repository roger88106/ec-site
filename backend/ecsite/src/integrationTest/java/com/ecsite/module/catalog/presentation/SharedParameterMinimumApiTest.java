package com.ecsite.module.catalog.presentation;

import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 契約の共通の引数・項目の下限の、入口の統合テスト
 *
 * <p>楽観ロックのバージョン（{@code parameters/Version} の minimum 0）と、画像の受付の大きさ（{@code contentLength} の minimum 0）は、
 * 下限ちょうどの値が入口の検証を通り、下限より 1 小さい値が入力の誤り（{@code INVALID_REQUEST}）の 400 になることを確認する。
 * どちらも商品の操作で使うので、商品の API で確認する。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class SharedParameterMinimumApiTest {

    // ============================== 定数 ==============================

    /**
     * 商品の API のパス
     */
    private static final String PRODUCTS = "/api/admin/products";

    /**
     * 作ったばかりの商品のバージョン（バージョンの下限と同じ 0）
     */
    private static final long NEW_PRODUCT_VERSION = 0;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * 商品カタログのテストデータ
     */
    private CatalogTestData catalog;

    /**
     * 下書きの商品の ID
     */
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogTestData(mockMvc, clock);
        var taxCategoryId = catalog.taxCategory("下限 " + UUID.randomUUID().toString().substring(0, 8), "10");
        productId = catalog.draft("MIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                taxCategoryId, "下限の商品").id;
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("楽観ロックのバージョンが下限の 0 なら、入口の検証を通り、操作が成り立つ")
    void acceptsVersionAtMinimum() throws Exception {
        // 操作と確認：作ったばかりの商品（バージョン 0）を、バージョン 0 で削除できる
        mockMvc.perform(delete(PRODUCTS + "/" + productId).param("version", String.valueOf(NEW_PRODUCT_VERSION)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("楽観ロックのバージョンが下限より 1 小さい -1 なら、入力の誤りの 400")
    void rejectsVersionBelowMinimum() throws Exception {
        // 操作と確認：-1 では INVALID_REQUEST の 400
        mockMvc.perform(delete(PRODUCTS + "/" + productId).param("version", String.valueOf(NEW_PRODUCT_VERSION - 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("AC-39 画像の大きさが契約の下限の 0 なら、入口の検証は通り、業務の検証（1 バイト以上）で拒否される")
    void contentLengthAtMinimumReachesBusinessValidation() throws Exception {
        // 操作と確認：0 は入力の誤りでなく、業務のエラー（IMAGE_TOO_LARGE）の 400
        catalog.createUpload(productId, "image/png", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMAGE_TOO_LARGE"));
    }

    @Test
    @DisplayName("AC-39 画像の大きさが契約の下限より 1 小さい -1 なら、入力の誤りの 400")
    void rejectsContentLengthBelowMinimum() throws Exception {
        // 操作と確認：-1 では INVALID_REQUEST の 400
        catalog.createUpload(productId, "image/png", -1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
