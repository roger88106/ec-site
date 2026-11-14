package com.ecsite.module.storefront.application;

import com.ecsite.module.storefront.application.port.CatalogDirectory;
import com.ecsite.module.storefront.application.port.OrderDirectory;
import com.ecsite.module.storefront.application.port.StockDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 店舗の情報を、設定の値から view にする操作の単体テスト
 *
 * <p>設定の値を view に写すことだけを確認する。店舗の API の応答は {@code StorefrontShopTest} が確認する。
 */
class StorefrontServiceShopTest {

    // ============================== 定数 ==============================

    /**
     * 設定の問い合わせ先
     */
    private static final String CONTACT_EMAIL = "contact@shop.example";

    // ============================== テスト ==============================

    @Test
    @DisplayName("店舗の情報は、設定の問い合わせ先を view にして返す")
    void returnsContactEmailFromProperties() {
        // 準備：問い合わせ先を設定したサービス
        var service = new StorefrontService(mock(CatalogDirectory.class), mock(StockDirectory.class),
                mock(OrderDirectory.class), new ShopProperties(CONTACT_EMAIL));

        // 操作と確認：設定の問い合わせ先
        assertThat(service.getShop().contactEmail()).isEqualTo(CONTACT_EMAIL);
    }
}
