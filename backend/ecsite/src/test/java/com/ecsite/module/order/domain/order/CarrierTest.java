package com.ecsite.module.order.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配送業者の単体テスト
 *
 * <p>注文の出荷のイベントに載せる配送業者への写しだけを確認する。出荷の API で業者を受け取り、詳細に返すことは
 * {@code ShipmentApiTest} が確認する。
 */
class CarrierTest {

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(Carrier.class)
    @DisplayName("AC-168 出荷のイベントには、出荷を登録した配送業者と同じ業者を載せる")
    void mapsToSameEventCarrier(Carrier carrier) {
        // 操作：出荷のイベントの配送業者に写す
        var eventCarrier = carrier.toEventCarrier();

        // 確認：同じ業者
        assertThat(eventCarrier.name()).isEqualTo(carrier.name());
    }
}
