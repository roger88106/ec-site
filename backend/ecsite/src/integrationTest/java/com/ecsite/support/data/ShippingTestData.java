package com.ecsite.support.data;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.order.application.OrderQueryService;
import com.ecsite.module.order.application.shipment.OrderShipmentCommandService;
import com.ecsite.module.payment.application.PaymentCommandService;
import com.ecsite.module.payment.application.model.view.PaymentCaptureView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 出荷のテストデータ
 *
 * <p>出荷を登録し、mock の物流サービスから配送状態の通知を送る。注文は、呼ぶ側が先に用意する。
 */
public class ShippingTestData {

    /**
     * MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * mock の物流サービス
     */
    private final MockCarrier mockCarrier;

    /**
     * Spring のコンテキスト（出荷の登録と、その売上確定の失敗を、HTTP を通らずに行うために使う）
     *
     * <p>出荷を登録しないときは null。
     */
    private final ApplicationContext applicationContext;

    /**
     * コンストラクタ
     *
     * @param mockMvc     MockMvc
     * @param mockCarrier mock の物流サービス
     */
    public ShippingTestData(MockMvc mockMvc, MockCarrier mockCarrier) {
        this(mockMvc, mockCarrier, null);
    }

    /**
     * コンストラクタ（出荷を登録するとき）
     *
     * @param mockMvc            MockMvc
     * @param mockCarrier        mock の物流サービス
     * @param applicationContext Spring のコンテキスト
     */
    public ShippingTestData(MockMvc mockMvc, MockCarrier mockCarrier, ApplicationContext applicationContext) {
        this.mockMvc = mockMvc;
        this.mockCarrier = mockCarrier;
        this.applicationContext = applicationContext;
    }

    /**
     * 他のテストが確定させた出荷と重ならない送り状番号（12 桁）を作る。
     *
     * @return 送り状番号
     */
    public static String trackingNumber() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000_000L) + 100_000_000_000L);
    }

    /**
     * 出荷を登録する（売上の確定と、注文の出荷済みへの変更）。
     *
     * <p>ロールバックするテストでは、トランザクションの外で呼ぶ前提のメソッド（{@code NEVER}）を本番の経路で呼べない。
     * そのため、HTTP を通らずに、出荷の登録の処理（{@code OrderPaymentGatewayService.ship}）の中身を、
     * テストのトランザクションの中で順に呼んで同じ状態を作る。
     * エンドポイント自身は {@code ShipmentApiTest} で確認する。
     *
     * @param orderId        注文 ID
     * @param carrier        物流サービス
     * @param trackingNumber 送り状番号
     */
    public void ship(UUID orderId, Carrier carrier, String trackingNumber) {
        var capture = capture(orderId, PaymentGateway.CaptureResult.CAPTURED);
        applicationContext.getBean(PaymentCommandService.class).markCaptured(capture.id());
        applicationContext.getBean(OrderShipmentCommandService.class).ship(orderId, com.ecsite.module.order.domain.order.Carrier.valueOf(carrier.name()), trackingNumber);
    }

    /**
     * 出荷の登録の売上確定を、与信期限切れで失敗させる（注文は出荷済みにならない）。
     *
     * <p>与信は、呼ぶ側が先に mock の決済サービスで切らしておく（{@code TestPaymentGateway.expireAuthorization}）。
     * {@link #ship} と同じ理由で、HTTP を通らずに、売上確定の処理（{@code PaymentGatewayService.capture}）の中身を、
     * テストのトランザクションの中で順に呼ぶ。
     *
     * @param orderId 注文 ID
     */
    public void failCaptureByExpiredAuthorization(UUID orderId) {
        var capture = capture(orderId, PaymentGateway.CaptureResult.AUTHORIZATION_EXPIRED);
        applicationContext.getBean(PaymentCommandService.class).markAuthorizationExpired(capture.id());
    }

    /**
     * 注文の売上確定の対象を取得し、mock の決済サービスで売上を確定して、結果を確認する。
     *
     * @param orderId  注文 ID
     * @param expected 期待する売上確定の結果
     * @return 売上確定の対象の支払
     */
    private PaymentCaptureView capture(UUID orderId, PaymentGateway.CaptureResult expected) {
        Objects.requireNonNull(applicationContext, "出荷を登録するときは ApplicationContext を渡すコンストラクタで作る");
        // 本番の前段の判定（出荷できる注文で、与信済みの支払）。満たさないなら、補助を使える状態ではない
        assertThat(applicationContext.getBean(OrderQueryService.class).get(orderId).actions().shippable())
                .as("出荷できる注文で呼ぶ").isTrue();
        // 本番と同じ操作で、支払を読み、売上確定を求めた時刻を記録する
        var capture = applicationContext.getBean(PaymentCommandService.class).requestCapture(new OrderId(orderId)).orElseThrow();
        assertThat(capture.status()).as("与信済みの支払で呼ぶ").isEqualTo(PaymentStatus.AUTHORIZED);
        var result = applicationContext.getBean(TestPaymentGateway.class).capture(new PaymentGateway.CaptureRequest(
                new OrderId(orderId), Objects.requireNonNull(capture.authorizationRef()), capture.amount()));
        assertThat(result).isEqualTo(expected);
        return capture;
    }

    /**
     * mock の物流サービスから、署名付きの配送状態の通知をエンドポイントへ送り、200 を確認する。
     *
     * @param carrier        物流サービス
     * @param trackingNumber 送り状番号
     * @param statusName     配送状態の名前
     */
    public void notifyStatus(Carrier carrier, String trackingNumber, String statusName) throws Exception {
        notifyStatus(mockCarrier.notification(carrier, trackingNumber, statusName));
    }

    /**
     * mock の物流サービスから、発生日時を指定した署名付きの配送状態の通知をエンドポイントへ送り、200 を確認する。
     *
     * @param carrier        物流サービス
     * @param trackingNumber 送り状番号
     * @param statusName     配送状態の名前
     * @param occurredAt     状態が変わった日時
     */
    public void notifyStatus(Carrier carrier, String trackingNumber, String statusName, Instant occurredAt) throws Exception {
        notifyStatus(mockCarrier.notification(carrier, trackingNumber, statusName, occurredAt));
    }

    private void notifyStatus(MockCarrier.SignedNotification notification) throws Exception {
        mockMvc.perform(post("/api/webhooks/carrier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Carrier-Signature", notification.signature())
                        .content(notification.body()))
                .andExpect(status().isOk());
    }
}
