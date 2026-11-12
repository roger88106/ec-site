package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.ShippingTestData;
import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.application.TrackingRequests;
import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.domain.Carrier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 物流の mock フラグを下ろした環境の、出荷の統合テスト
 *
 * <p>接続していない物流サービスは通知を送らないので、配送状態は出荷済みから進まない。
 * mock の物流サービスの Bean がないので、mock に依存する補助は使わない。
 * 出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
@TestPropertySource(properties = "ecsite.shipping.mock.enabled=false")
class UnconnectedCarrierTrackingTest {

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-15T01:00:00Z");

    @Autowired
    ShippingApi shippingApi;

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 物流サービスの追跡（追跡の開始の依頼が届いたことを確認するため、spy にする）
     */
    @MockitoSpyBean
    CarrierTracking tracking;

    @Test
    @DisplayName("AC-410-1 物流のフラグを下ろしても出荷を登録でき、配送状態は出荷済みのままで、追跡の開始の依頼は未完了で残らない")
    void shipsWithoutCarrierConnection() {
        var orderId = UUID.randomUUID();
        var trackingNumber = ShippingTestData.trackingNumber();

        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, trackingNumber, SHIPPED_AT);

        asyncEvents.awaitHandled(event -> TrackingRequests.isFor(event, List.of(trackingNumber)));
        // 依頼が発行されないときも待つ処理はすぐに戻るので、依頼が届いたことも確認する
        verify(tracking).startTracking(Carrier.YAMATO, trackingNumber);
        assertThat(shippingApi.find(List.of(orderId)))
                .singleElement()
                .satisfies(summary -> assertThat(summary.status()).isEqualTo(ShipmentSummary.Status.SHIPPED));
    }
}
