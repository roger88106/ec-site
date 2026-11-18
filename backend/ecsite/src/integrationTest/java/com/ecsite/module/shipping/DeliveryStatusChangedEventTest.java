package com.ecsite.module.shipping;

import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import com.ecsite.module.shipping.event.DeliveryStatusChanged.Status;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.ShippingTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * イベント「配送状態の変化」の統合テスト
 *
 * <p>出荷を作ったときと、通知で配送状態が進んだときだけイベントが出て、重ねて届いた通知と戻る通知では出ないことを確認する。
 * 通知で配送状態がどう進むかは {@code CarrierWebhookTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class DeliveryStatusChangedEventTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     *
     * <p>日本時間の 2026-10-15 11:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-15T02:00:00Z");

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "998877665544";

    // ============================== Bean ==============================

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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("イベントが出るとき")
    class Emitted {

        @Test
        @DisplayName("出荷を作ると、前の配送状態がなく、後が出荷済みのイベントが出る")
        void emitsShippedOnCreate() {
            // 操作：出荷を作る
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);

            // 確認：出荷済みのイベントだけ
            assertThat(events.stream(DeliveryStatusChanged.class))
                    .extracting(DeliveryStatusChanged::orderId, DeliveryStatusChanged::from, DeliveryStatusChanged::to,
                            DeliveryStatusChanged::occurredAt)
                    .containsExactly(tuple(orderId, null, Status.SHIPPED, NOW));
        }

        @Test
        @DisplayName("通知で配送状態が進むたびに、前と後の配送状態と、通知を反映した日時のイベントが出る")
        void emitsOnEachAdvance() throws Exception {
            // 準備：出荷を作る
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);

            // 操作：1 分後に配達中、3 分後に配達完了を通知する
            at(1);
            notifyStatus("配達中");
            at(3);
            notifyStatus("配達完了");

            // 確認：作成と、2 回の前進のイベントが順に出る
            assertThat(events.stream(DeliveryStatusChanged.class))
                    .extracting(DeliveryStatusChanged::orderId, DeliveryStatusChanged::from, DeliveryStatusChanged::to,
                            DeliveryStatusChanged::occurredAt)
                    .containsExactly(
                            tuple(orderId, null, Status.SHIPPED, NOW),
                            tuple(orderId, Status.SHIPPED, Status.IN_TRANSIT, minutes(1)),
                            tuple(orderId, Status.IN_TRANSIT, Status.DELIVERED, minutes(3)));
        }

        @Test
        @DisplayName("DC-548-5 通知で配送状態が進んだときのイベントの発生日時は、処理した時刻でなく、通知の発生日時になる")
        void usesOccurredAtOfNotification() throws Exception {
            // 準備：出荷を作り、時計を 10 分後にする
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);
            at(10);

            // 操作：3 分後に起きた配達中を通知する
            new ShippingTestData(mockMvc, mockCarrier).notifyStatus(Carrier.YAMATO, TRACKING_NUMBER, "配達中", minutes(3));

            // 確認：最後のイベントの発生日時が、通知の発生日時
            assertThat(events.stream(DeliveryStatusChanged.class)).last()
                    .extracting(DeliveryStatusChanged::orderId, DeliveryStatusChanged::to, DeliveryStatusChanged::occurredAt)
                    .containsExactly(orderId, Status.IN_TRANSIT, minutes(3));
        }

        @Test
        @DisplayName("返送の通知で、出荷済みから返送へのイベントが出る")
        void emitsOnReturn() throws Exception {
            // 準備：出荷を作る
            shippingApi.create(UUID.randomUUID(), ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);

            // 操作：返品を通知する
            notifyStatus("返品");

            // 確認：最後のイベントが、出荷済みから返送
            assertThat(events.stream(DeliveryStatusChanged.class)).last()
                    .extracting(DeliveryStatusChanged::from, DeliveryStatusChanged::to)
                    .containsExactly(Status.SHIPPED, Status.RETURNED);
        }
    }

    @Nested
    @DisplayName("イベントが出ないとき")
    class NotEmitted {

        @Test
        @DisplayName("同じ配送状態の通知が重ねて届いても、2 回目ではイベントが出ない")
        void doesNotEmitOnDuplicate() throws Exception {
            // 準備：出荷を作り、1 分後に配達中を通知する
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);
            at(1);
            notifyStatus("配達中");

            // 操作：2 分後に、もう一度配達中を通知する
            at(2);
            notifyStatus("配達中");

            // 確認：作成と、1 回目の配達中のイベントだけ
            assertThat(events.stream(DeliveryStatusChanged.class))
                    .extracting(DeliveryStatusChanged::orderId, DeliveryStatusChanged::from, DeliveryStatusChanged::to,
                            DeliveryStatusChanged::occurredAt)
                    .containsExactly(
                            tuple(orderId, null, Status.SHIPPED, NOW),
                            tuple(orderId, Status.SHIPPED, Status.IN_TRANSIT, minutes(1)));
        }

        @Test
        @DisplayName("配達完了の後に、配達中と返送の通知が届いても、イベントが出ない")
        void doesNotEmitOnGoingBack() throws Exception {
            // 準備：出荷を作り、3 分後に配達完了を通知する
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, TRACKING_NUMBER, NOW);
            at(3);
            notifyStatus("配達完了");

            // 操作：4 分後に、配達中と返品を通知する
            at(4);
            notifyStatus("配達中");
            notifyStatus("返品");

            // 確認：作成と、配達完了のイベントだけ
            assertThat(events.stream(DeliveryStatusChanged.class))
                    .extracting(DeliveryStatusChanged::orderId, DeliveryStatusChanged::from, DeliveryStatusChanged::to,
                            DeliveryStatusChanged::occurredAt)
                    .containsExactly(
                            tuple(orderId, null, Status.SHIPPED, NOW),
                            tuple(orderId, Status.SHIPPED, Status.DELIVERED, minutes(3)));
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 時計を、出荷日時の指定の分の後にする。
     *
     * @param minutes 出荷日時からの分
     */
    private void at(int minutes) {
        clock.setInstant(minutes(minutes));
    }

    /**
     * 出荷日時の、指定の分の後の時刻を返す。
     *
     * @param minutes 出荷日時からの分
     * @return 時刻
     */
    private static Instant minutes(int minutes) {
        return NOW.plus(Duration.ofMinutes(minutes));
    }

    /**
     * mock の物流サービスから、配送状態の通知をエンドポイントへ送る。
     *
     * @param statusName 物流サービスの状態の名前
     * @throws Exception 送れなかったとき
     */
    private void notifyStatus(String statusName) throws Exception {
        new ShippingTestData(mockMvc, mockCarrier).notifyStatus(Carrier.YAMATO, TRACKING_NUMBER, statusName);
    }
}
