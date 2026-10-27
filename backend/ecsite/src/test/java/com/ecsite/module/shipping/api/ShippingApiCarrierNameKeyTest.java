package com.ecsite.module.shipping.api;

import com.ecsite.module.shipping.application.ShipmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 出荷の公開 API の単体テスト
 */
class ShippingApiCarrierNameKeyTest {

    private final ShippingApi api = new ShippingApi(mock(ShipmentService.class));

    @ParameterizedTest
    @EnumSource(ShipmentSummary.Carrier.class)
    @DisplayName("配送業者の表示名のキーは、全ての配送業者で文言のファイルにある")
    void carrierNameKeyExistsInMessages(ShipmentSummary.Carrier carrier) throws IOException {
        var messages = new Properties();
        try (var reader = new InputStreamReader(
                getClass().getResourceAsStream("/messages.properties"), StandardCharsets.UTF_8)) {
            messages.load(reader);
        }

        var key = api.carrierNameKey(carrier);

        assertThat(messages.getProperty(key)).isNotBlank();
    }

    @Test
    @DisplayName("配送業者の表示名のキーは、配送業者ごとに違う")
    void carrierNameKeysAreDistinct() {
        var keys = Arrays.stream(ShipmentSummary.Carrier.values()).map(api::carrierNameKey).toList();

        assertThat(keys).doesNotHaveDuplicates();
    }
}
