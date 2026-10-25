package com.ecsite.module.shipping;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.module.shipping.api.ShipmentSummary;
import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import com.ecsite.module.shipping.event.DeliveryStatusChanged.Status;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * イベント「配送状態の変化」の統合テスト。<br />
 * 出荷を作ったときと、通知で配送状態が進んだときだけ出て、重ねて届いた通知・戻る通知では出ないことを確認する。
 * テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@RecordApplicationEvents
@Transactional
class DeliveryStatusChangedEventTest {

    /**
     * 出荷日時
     */
    private static final Instant NOW = Instant.parse("2026-10-15T02:00:00Z");

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "998877665544";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ShippingApi shippingApi;

    @Autowired
    MockCarrier mockCarrier;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("出荷を作ると出荷済み、通知で進むたびに前と後の配送状態が出る。重ねて届いた通知と戻る通知では出ない")
    void emitsOnlyWhenAdvanced() throws Exception {
        var orderId = UUID.randomUUID();
        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);
        at(1);
        notifyStatus("配達中");
        at(2);
        notifyStatus("配達中");
        at(3);
        notifyStatus("配達完了");
        at(4);
        notifyStatus("配達中");
        notifyStatus("返品");

        assertThat(events.stream(DeliveryStatusChanged.class))
                .extracting(DeliveryStatusChanged::orderId, DeliveryStatusChanged::from, DeliveryStatusChanged::to,
                        DeliveryStatusChanged::occurredAt)
                .containsExactly(
                        tuple(orderId, null, Status.SHIPPED, NOW),
                        tuple(orderId, Status.SHIPPED, Status.IN_TRANSIT, minutes(1)),
                        tuple(orderId, Status.IN_TRANSIT, Status.DELIVERED, minutes(3)));
    }

    @Test
    @DisplayName("返送の通知で、返送が出る")
    void emitsOnReturn() throws Exception {
        var orderId = UUID.randomUUID();
        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);
        notifyStatus("返品");

        assertThat(events.stream(DeliveryStatusChanged.class)).last()
                .extracting(DeliveryStatusChanged::from, DeliveryStatusChanged::to)
                .containsExactly(Status.SHIPPED, Status.RETURNED);
    }

    // ============================== 補助 ==============================

    /**
     * 時計を NOW の指定の分の後にする
     */
    private void at(int minutes) {
        clock.setInstant(minutes(minutes));
    }

    /**
     * NOW の指定の分の後
     */
    private static Instant minutes(int minutes) {
        return NOW.plus(Duration.ofMinutes(minutes));
    }

    /**
     * mock の物流サービスから、配送状態の通知をエンドポイントへ送る
     */
    private void notifyStatus(String statusName) throws Exception {
        new ShippingTestData(mockMvc, mockCarrier).notifyStatus(Carrier.YAMATO, TRACKING_NUMBER, statusName);
    }
}
