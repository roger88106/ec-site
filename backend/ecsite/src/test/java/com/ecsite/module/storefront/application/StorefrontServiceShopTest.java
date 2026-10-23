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
 */
class StorefrontServiceShopTest {

    @Test
    @DisplayName("店舗の情報は、設定の問い合わせ先を view にして返す")
    void returnsContactEmailFromProperties() {
        var service = new StorefrontService(mock(CatalogDirectory.class), mock(StockDirectory.class),
                mock(OrderDirectory.class), new ShopProperties("contact@shop.example"));

        assertThat(service.getShop().contactEmail()).isEqualTo("contact@shop.example");
    }
}
