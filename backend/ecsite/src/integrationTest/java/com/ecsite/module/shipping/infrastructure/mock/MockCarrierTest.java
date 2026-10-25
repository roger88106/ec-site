package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.IntegrationTest;
import com.ecsite.ShippingTestData;
import com.ecsite.module.shipping.api.ShipmentSummary;
import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.domain.Carrier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mock の物流サービスの統合テスト（T9209、NFR-9、AC-174〜178）。<br />
 * 実際の HTTP（ランダムなポート）で、画面のボタンと、テストから送る通知が、署名の検証を含む通知のエンドポイントを通ることを確認する。
 * 出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MockCarrierTest {

    /**
     * 出荷日時。画面の一覧（新しい順に 50 件）の先頭に並ぶよう、ほかのテストの出荷より後にする。このテストの出荷は確定して残るので、全体から新しい順に読む別のテストは、これより後の日時（3000 年）を使う
     */
    private static final Instant SHIPPED_AT = Instant.parse("2095-01-01T00:00:00Z");

    @LocalServerPort
    int port;

    @Autowired
    ShippingApi shippingApi;

    @Autowired
    MockCarrier mockCarrier;

    /**
     * 移動（リダイレクト）を追わない HTTP の呼び出し
     */
    private final RestClient http = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build();

    @Test
    @DisplayName("NFR-9 mock の画面は、出荷の一覧と、出荷ごとの「配送中」「配達完了」「返送」のボタンを示す")
    void showsShipmentsWithButtons() {
        var shipment = shipped(ShipmentSummary.Carrier.YAMATO);

        var page = http.get().uri(baseUrl() + "/mock-carrier").retrieve().body(String.class);

        assertThat(page)
                .contains(shipment.orderId().toString(), shipment.trackingNumber(), "ヤマト運輸", "出荷済み")
                .contains(">配送中</button>", ">配達完了</button>", ">返送</button>");
    }

    @Test
    @DisplayName("AC-174 画面のボタンを押すと、署名付きの通知がエンドポイントに送られ、配送状態が進む。戻る通知では戻らない")
    void buttonsAdvanceThroughWebhook() {
        var shipment = shipped(ShipmentSummary.Carrier.YAMATO);

        assertThat(press(shipment, "IN_TRANSIT")).isEqualTo(303);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.IN_TRANSIT);
        assertThat(press(shipment, "DELIVERED")).isEqualTo(303);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);

        // 同じ順位の返送、低い順位の配送中は、反映されない
        assertThat(press(shipment, "RETURNED")).isEqualTo(303);
        assertThat(press(shipment, "IN_TRANSIT")).isEqualTo(303);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);
    }

    @Test
    @DisplayName("AC-174 返送のボタンを押すと、配送状態が返送になる")
    void returnButton() {
        var shipment = shipped(ShipmentSummary.Carrier.YAMATO);

        assertThat(press(shipment, "RETURNED")).isEqualTo(303);

        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.RETURNED);
    }

    @Test
    @DisplayName("NFR-9 テストから送る通知は、同じ通知を重ねても 1 回だけ反映され、途中の欠けと順序の入れ替わりでも戻らない")
    void testHooksSendDuplicatesMissingAndOutOfOrder() {
        var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
        var inTransit = mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達中");
        var delivered = mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達完了");

        // 重複
        assertThat(mockCarrier.send(baseUrl(), inTransit)).isEqualTo(200);
        assertThat(mockCarrier.send(baseUrl(), inTransit)).isEqualTo(200);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.IN_TRANSIT);

        // 途中の欠け（荷物受付や作業店通過が届かないまま配達完了）
        assertThat(mockCarrier.send(baseUrl(), delivered)).isEqualTo(200);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);

        // 順序の入れ替わり（配達完了の後に配達中）
        assertThat(mockCarrier.send(baseUrl(), inTransit)).isEqualTo(200);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);
    }

    @Test
    @DisplayName("AC-178 テストから送る署名の誤りは 401、合わない送り状番号は 200 で、どちらも配送状態を変えない")
    void testHooksSendInvalidSignatureAndUnknownTrackingNumber() {
        var shipment = shipped(ShipmentSummary.Carrier.YAMATO);

        var invalid = TestCarrierNotifications.withInvalidSignature(
                mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達完了"));
        var unknown = mockCarrier.notification(Carrier.YAMATO, ShippingTestData.trackingNumber(), "配達完了");

        assertThat(mockCarrier.send(baseUrl(), invalid)).isEqualTo(401);
        assertThat(mockCarrier.send(baseUrl(), unknown)).isEqualTo(200);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.SHIPPED);
    }

    @Test
    @DisplayName("NFR-9 ヤマト運輸の表示の名前でだけ通知できるので、ほかの配送業者の出荷にはボタンを示さず、押しても何も変えない")
    void doesNotNotifyOtherCarriers() {
        var shipment = shipped(ShipmentSummary.Carrier.SAGAWA);

        var page = http.get().uri(baseUrl() + "/mock-carrier").retrieve().body(String.class);
        var status = press(shipment, "DELIVERED");

        assertThat(page).contains(shipment.trackingNumber(), "佐川急便");
        assertThat(status).isEqualTo(200);
        assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.SHIPPED);
    }

    @Test
    @DisplayName("NFR-9 出荷のない注文のボタンは 404")
    void unknownOrderIsNotFound() {
        var status = http.post().uri(baseUrl() + "/mock-carrier/orders/" + UUID.randomUUID() + "/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("status=DELIVERED")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(404);
    }

// ============================== 補助 ==============================

    /**
     * 作った出荷
     */
    private record Created(UUID orderId, String trackingNumber) {
    }

    /**
     * バックエンドの URL
     */
    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * 出荷を作る。送り状番号は、ほかのテストが確定した出荷と重ならない。
     */
    private Created shipped(ShipmentSummary.Carrier carrier) {
        var orderId = UUID.randomUUID();
        var trackingNumber = ShippingTestData.trackingNumber();
        shippingApi.create(orderId, carrier, trackingNumber, SHIPPED_AT);
        return new Created(orderId, trackingNumber);
    }

    /**
     * 画面のボタンを押す。
     *
     * @return 応答のステータス
     */
    private int press(Created shipment, String status) {
        return http.post().uri(baseUrl() + "/mock-carrier/orders/" + shipment.orderId() + "/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("status=" + status)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    /**
     * 出荷の配送状態を読む。
     */
    private ShipmentSummary.Status statusOf(Created shipment) {
        return shippingApi.find(List.of(shipment.orderId())).getFirst().status();
    }
}
