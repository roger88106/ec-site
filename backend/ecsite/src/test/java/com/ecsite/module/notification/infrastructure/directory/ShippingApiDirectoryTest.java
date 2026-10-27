package com.ecsite.module.notification.infrastructure.directory;

import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.application.ShipmentService;
import com.ecsite.module.shipping.domain.Carrier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 配送業者の表示名のキーを出荷モジュールから得る腐敗防止層の単体テスト
 */
class ShippingApiDirectoryTest {

    private final ShippingApiDirectory directory = new ShippingApiDirectory(new ShippingApi(mock(ShipmentService.class)));

    @ParameterizedTest(name = "{0}")
    @EnumSource(OrderShipped.Carrier.class)
    @DisplayName("出荷イベントの全ての配送業者で、出荷モジュールの同じ名前の配送業者の表示名のキーを返す")
    void returnsNameKeyOfSameCarrier(OrderShipped.Carrier carrier) {
        assertThat(directory.nameKey(carrier)).isEqualTo(Carrier.valueOf(carrier.name()).nameMessageKey());
    }
}
