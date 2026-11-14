package com.ecsite.module.shipping.api;

import com.ecsite.module.shipping.api.model.ShipmentSummary;
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
 * 出荷の公開 API の、配送業者の表示名のキーの単体テスト
 *
 * <p>配送業者の表示名のキーが文言のファイルにあり、配送業者ごとに違うことだけを確認する。表示名を画面とメールに
 * 出すことは、それぞれのテストが確認する。
 */
class ShippingApiCarrierNameKeyTest {

    // ============================== Bean ==============================

    /**
     * テストの対象
     */
    private final ShippingApi api = new ShippingApi(mock(ShipmentService.class));

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(ShipmentSummary.Carrier.class)
    @DisplayName("配送業者の表示名のキーは、全ての配送業者で文言のファイルにある")
    void carrierNameKeyExistsInMessages(ShipmentSummary.Carrier carrier) throws IOException {
        // 準備：文言のファイル
        var messages = messages();

        // 操作：配送業者の表示名のキーを得る
        var key = api.carrierNameKey(carrier);

        // 確認：キーの文言がある
        assertThat(messages.getProperty(key)).isNotBlank();
    }

    @Test
    @DisplayName("配送業者の表示名のキーは、配送業者ごとに違う")
    void carrierNameKeysAreDistinct() {
        // 操作：全ての配送業者のキーを得る
        var keys = Arrays.stream(ShipmentSummary.Carrier.values()).map(api::carrierNameKey).toList();

        // 確認：重複がない
        assertThat(keys).doesNotHaveDuplicates();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 文言のファイル（{@code messages.properties}）を読む。
     *
     * @return 文言
     * @throws IOException 読めないとき
     */
    private Properties messages() throws IOException {
        var messages = new Properties();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/messages.properties"), StandardCharsets.UTF_8)) {
            messages.load(reader);
        }
        return messages;
    }
}
