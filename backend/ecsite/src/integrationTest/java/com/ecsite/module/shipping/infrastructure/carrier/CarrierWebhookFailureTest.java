package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.ShippingTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 物流サービスの通知の処理が失敗したときの統合テスト
 *
 * <p>出荷のリポジトリを spy にして、配送状態の保存を 1 回だけ失敗させる。
 * spy で Spring のコンテキストが変わるので、{@code CarrierWebhookTest} と分ける。
 * 出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
@RecordApplicationEvents
class CarrierWebhookFailureTest {

    // ============================== 定数 ==============================

    /**
     * 通知を受け付ける時刻
     *
     * <p>日本時間の 2026-10-15 11:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-15T02:00:00Z");

    // ============================== Bean ==============================

    /**
     * 通知のエンドポイントを呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * 出荷の API（出荷を作るのに使う）
     */
    @Autowired
    ShippingApi shippingApi;

    /**
     * mock の物流サービス（署名付きの通知を作るのに使う）
     */
    @Autowired
    MockCarrier mockCarrier;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 発行されたイベント
     */
    @Autowired
    ApplicationEvents events;

    /**
     * 配送状態の保存を失敗させる、出荷のリポジトリ
     */
    @MockitoSpyBean
    ShipmentRepository repository;

    /**
     * 時計を通知を受け付ける時刻にする。
     */
    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("BR-22 処理の途中で失敗した通知は 5xx を返し、配送状態もイベントも残さず、同じ通知の再送で反映される")
    void retriesFailedNotification() throws Exception {
        // 準備：出荷済みの出荷と配達中の通知、次の配送状態の保存の失敗
        var orderId = UUID.randomUUID();
        var trackingNumber = ShippingTestData.trackingNumber();
        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, trackingNumber, NOW);
        var notification = mockCarrier.notification(Carrier.YAMATO, trackingNumber, "配達中");
        doThrow(new DataAccessResourceFailureException("DB の失敗")).doCallRealMethod()
                .when(repository).updateProgress(anyCollection());

        // 操作と確認：1 回目は 5xx
        send(notification).andExpect(status().is5xxServerError());

        // 確認：出荷済みのまま、配送状態の変化のイベントは作成のときの 1 件だけ
        assertThat(statusOf(orderId)).isEqualTo(DeliveryStatus.SHIPPED);
        assertThat(changesOf(orderId)).containsExactly(DeliveryStatusChanged.Status.SHIPPED);

        // 操作と確認：同じ通知の再送は 200
        send(notification).andExpect(status().isOk());

        // 確認：配送中になり、配送中へのイベントが 1 件出る
        assertThat(statusOf(orderId)).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(changesOf(orderId)).containsExactly(DeliveryStatusChanged.Status.SHIPPED, DeliveryStatusChanged.Status.IN_TRANSIT);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 出荷の配送状態を読む。
     *
     * @param orderId 注文 ID
     * @return 配送状態
     */
    private DeliveryStatus statusOf(UUID orderId) {
        return repository.findAllByOrderIds(List.of(orderId)).getFirst().status();
    }

    /**
     * 注文の配送状態の変化のイベントの、後の配送状態を、発行の順に返す。
     *
     * @param orderId 注文 ID
     * @return 後の配送状態
     */
    private List<DeliveryStatusChanged.Status> changesOf(UUID orderId) {
        return events.stream(DeliveryStatusChanged.class).filter(event -> event.orderId().equals(orderId))
                .map(DeliveryStatusChanged::to).toList();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 署名付きの通知を、エンドポイントへ送る。
     *
     * @param notification 署名付きの通知
     * @return 応答
     * @throws Exception 送れなかったとき
     */
    private ResultActions send(MockCarrier.SignedNotification notification) throws Exception {
        return mockMvc.perform(post("/api/webhooks/carrier")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Carrier-Signature", notification.signature())
                .content(notification.body()));
    }
}
