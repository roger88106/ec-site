package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.application.TrackingRequests;
import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.support.ShipmentTestData;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.ShippingTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 物流の mock フラグを下ろした環境の、出荷の統合テスト
 *
 * <p>接続していない物流サービスでも出荷を登録でき、追跡の開始の依頼が完了することを確認する。
 * 接続していない物流サービスは通知を送らないので、配送状態は出荷済みから進まない。
 * 起動時の警告のログは、このクラスでは確認しない。
 *
 * <p>mock の物流サービスの Bean がないので、mock に依存する補助は使わない。
 * 出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
@TestPropertySource(properties = "ecsite.shipping.mock.enabled=false")
class UnconnectedCarrierTrackingTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-15T01:00:00Z");

    // ============================== Bean ==============================

    @Autowired
    ShippingApi shippingApi;

    /**
     * 出荷の作成を、呼んだ側のトランザクションに加えるためのテンプレート
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 出荷を作る補助
     */
    private ShipmentTestData shipments;

    @Autowired
    AsyncEvents asyncEvents;

    /**
     * 物流サービスの追跡
     *
     * <p>追跡の開始の依頼が届いたことを確認するため、spy にする。
     */
    @MockitoSpyBean
    CarrierTracking tracking;

    @BeforeEach
    void setUp() {
        shipments = new ShipmentTestData(shippingApi, transactionTemplate);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-410-1 物流のフラグを下ろしても、出荷を登録すると追跡の開始の依頼が届き、未完了で残らない")
    void completesTrackingRequest() {
        // 操作：出荷を登録する
        var trackingNumber = ShippingTestData.trackingNumber();
        shipments.create(UUID.randomUUID(), ShipmentSummary.Carrier.YAMATO, trackingNumber, SHIPPED_AT);

        // 確認：依頼の処理が終わる（未完了なら待つ処理が上限の時間で失敗する）
        asyncEvents.awaitHandled(event -> TrackingRequests.isFor(event, List.of(trackingNumber)));

        // 確認：依頼が物流サービスの追跡に届いた（依頼が発行されないときも、待つ処理はすぐに戻るため）
        verify(tracking).startTracking(Carrier.YAMATO, trackingNumber);
    }

    @Test
    @DisplayName("AC-410-1 物流のフラグを下ろしても出荷を登録でき、配送状態は出荷済みのまま")
    void staysShipped() {
        // 準備：出荷を登録し、追跡の開始の依頼が処理されるまで待つ
        var orderId = UUID.randomUUID();
        var trackingNumber = ShippingTestData.trackingNumber();
        shipments.create(orderId, ShipmentSummary.Carrier.YAMATO, trackingNumber, SHIPPED_AT);
        asyncEvents.awaitHandled(event -> TrackingRequests.isFor(event, List.of(trackingNumber)));

        // 操作と確認：出荷があり、出荷済みのまま
        assertThat(shippingApi.find(List.of(orderId)))
                .singleElement()
                .satisfies(summary -> assertThat(summary.status()).isEqualTo(ShipmentSummary.Status.SHIPPED));
    }
}
