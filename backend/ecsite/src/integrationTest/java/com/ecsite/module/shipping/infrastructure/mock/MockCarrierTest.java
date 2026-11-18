package com.ecsite.module.shipping.infrastructure.mock;

import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.ShippingTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mock の物流サービスの統合テスト
 *
 * <p>実際の HTTP（ランダムなポート）で、mock の画面のボタンと、テストから送る通知が、署名の検証を含む通知のエンドポイントを通ることを確認する。
 * 通知ごとの配送状態の進み方の全ては {@code CarrierWebhookTest} が確認する。
 *
 * <p>出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MockCarrierTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     *
     * <p>画面の一覧（新しい順に 50 件）の先頭に並ぶよう、ほかのテストの出荷より後にする。
     * このテストの出荷は確定して残るので、全体から新しい順に読む別のテストは、これより後の日時（3000 年）を使う。
     */
    private static final Instant SHIPPED_AT = Instant.parse("2095-01-01T00:00:00Z");

    /**
     * 画面のボタンを押したときの応答のステータス（画面へ戻す移動）
     */
    private static final int SEE_OTHER = 303;

    // ============================== Bean ==============================

    @LocalServerPort
    int port;

    @Autowired
    ShippingApi shippingApi;

    /**
     * 出荷の作成を、呼んだ側のトランザクションに加えるためのテンプレート
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    MockCarrier mockCarrier;

    @Autowired
    MutableClock clock;

    /**
     * 移動（リダイレクト）を追わない HTTP の呼び出し
     */
    private final RestClient http = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
            .build();

    /**
     * 時計を出荷日時にする。
     *
     * <p>mock が送る通知は、発生日時が時計の時刻になる。出荷日時より前にならないよう、時計を出荷日時に合わせる。
     */
    @BeforeEach
    void setUp() {
        clock.setInstant(SHIPPED_AT);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("mock の画面")
    class Page {

        @Test
        @DisplayName("NFR-9 mock の画面は、出荷の注文 ID、送り状番号、配送業者、配送状態を示す")
        void showsShipment() {
            // 準備：ヤマト運輸の出荷
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);

            // 操作：画面を開く
            var page = page();

            // 確認：出荷の項目が並ぶ
            assertThat(page).contains(shipment.orderId().toString(), shipment.trackingNumber(), "ヤマト運輸", "出荷済み");
        }

        @Test
        @DisplayName("NFR-9 mock の画面は、出荷ごとに「配送中」「配達完了」「返送」のボタンを示す")
        void showsButtons() {
            // 準備：ヤマト運輸の出荷
            shipped(ShipmentSummary.Carrier.YAMATO);

            // 操作：画面を開く
            var page = page();

            // 確認：3 つのボタンがある
            assertThat(page).contains(">配送中</button>", ">配達完了</button>", ">返送</button>");
        }

        @Test
        @DisplayName("NFR-9 ヤマト運輸以外の配送業者の出荷も、画面に並ぶ")
        void showsOtherCarrierShipment() {
            // 準備：佐川急便の出荷
            var shipment = shipped(ShipmentSummary.Carrier.SAGAWA);

            // 操作：画面を開く
            var page = page();

            // 確認：送り状番号と配送業者が並ぶ
            assertThat(page).contains(shipment.trackingNumber(), "佐川急便");
        }
    }

    @Nested
    @DisplayName("画面のボタン")
    class Buttons {

        @Test
        @DisplayName("AC-174 配送中のボタンを押すと、署名付きの通知がエンドポイントに送られ、配送状態が配送中になる")
        void inTransitButton() {
            // 準備：出荷済みの出荷
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);

            // 操作：配送中のボタンを押す
            var status = press(shipment, "IN_TRANSIT");

            // 確認：画面へ戻り、配送中になる
            assertThat(status).isEqualTo(SEE_OTHER);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.IN_TRANSIT);
        }

        @Test
        @DisplayName("AC-174 配送中の出荷で配達完了のボタンを押すと、配送状態が配達完了になる")
        void deliveredButton() {
            // 準備：配送中の出荷
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
            assertThat(press(shipment, "IN_TRANSIT")).isEqualTo(SEE_OTHER);

            // 操作：配達完了のボタンを押す
            var status = press(shipment, "DELIVERED");

            // 確認：画面へ戻り、配達完了になる
            assertThat(status).isEqualTo(SEE_OTHER);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);
        }

        @Test
        @DisplayName("AC-174 返送のボタンを押すと、配送状態が返送になる")
        void returnButton() {
            // 準備：出荷済みの出荷
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);

            // 操作：返送のボタンを押す
            var status = press(shipment, "RETURNED");

            // 確認：画面へ戻り、返送になる
            assertThat(status).isEqualTo(SEE_OTHER);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.RETURNED);
        }

        @Test
        @DisplayName("AC-174 配達完了の後に、返送と配送中のボタンを押しても、配達完了のまま")
        void doesNotGoBackByButtons() {
            // 準備：配送中を経て、配達完了になった出荷
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
            assertThat(press(shipment, "IN_TRANSIT")).isEqualTo(SEE_OTHER);
            assertThat(press(shipment, "DELIVERED")).isEqualTo(SEE_OTHER);

            // 操作：同じ順位の返送と、低い順位の配送中のボタンを押す
            var returned = press(shipment, "RETURNED");
            var inTransit = press(shipment, "IN_TRANSIT");

            // 確認：どちらも画面へ戻るが、配達完了のまま
            assertThat(returned).isEqualTo(SEE_OTHER);
            assertThat(inTransit).isEqualTo(SEE_OTHER);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);
        }

        @Test
        @DisplayName("NFR-9 ヤマト運輸以外の配送業者の出荷のボタンを押しても、200 で配送状態を変えない")
        void doesNotNotifyOtherCarriers() {
            // 準備：佐川急便の出荷（通知はヤマト運輸の表示の名前でだけ作れる）
            var shipment = shipped(ShipmentSummary.Carrier.SAGAWA);

            // 操作：配達完了のボタンを押す
            var status = press(shipment, "DELIVERED");

            // 確認：通知を送らずに 200 を返し、出荷済みのまま
            assertThat(status).isEqualTo(200);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.SHIPPED);
        }

        @Test
        @DisplayName("NFR-9 出荷のない注文のボタンは 404")
        void unknownOrderIsNotFound() {
            // 操作：出荷のない注文のボタンを押す
            var status = http.post().uri(baseUrl() + "/mock-carrier/orders/" + UUID.randomUUID() + "/notify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("status=DELIVERED")
                    .exchange((request, response) -> response.getStatusCode().value());

            // 確認：404
            assertThat(status).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("テストから送る通知")
    class TestHooks {

        @Test
        @DisplayName("NFR-9 同じ通知を重ねて送っても、1 回だけ反映される")
        void sendsDuplicates() {
            // 準備：出荷済みの出荷と、配達中の通知
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
            var inTransit = mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達中");

            // 操作：同じ通知を 2 回送る
            var first = mockCarrier.send(baseUrl(), inTransit);
            var second = mockCarrier.send(baseUrl(), inTransit);

            // 確認：どちらも 200 で、配送中
            assertThat(first).isEqualTo(200);
            assertThat(second).isEqualTo(200);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.IN_TRANSIT);
        }

        @Test
        @DisplayName("NFR-9 途中の通知が欠けても、後の通知で進む")
        void sendsWithMissing() {
            // 準備：配送中の出荷（荷物受付や作業店通過の通知は送らない）
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
            assertThat(mockCarrier.send(baseUrl(), mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達中")))
                    .isEqualTo(200);

            // 操作：配達完了を送る
            var status = mockCarrier.send(baseUrl(), mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達完了"));

            // 確認：200 で、配達完了
            assertThat(status).isEqualTo(200);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);
        }

        @Test
        @DisplayName("NFR-9 順序が入れ替わって届いても、配送状態は戻らない")
        void sendsOutOfOrder() {
            // 準備：配送中を経て、配達完了になった出荷
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
            var inTransit = mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達中");
            assertThat(mockCarrier.send(baseUrl(), inTransit)).isEqualTo(200);
            assertThat(mockCarrier.send(baseUrl(), mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達完了")))
                    .isEqualTo(200);

            // 操作：配達完了の後に、配達中を送る
            var status = mockCarrier.send(baseUrl(), inTransit);

            // 確認：200 で、配達完了のまま
            assertThat(status).isEqualTo(200);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.DELIVERED);
        }

        @Test
        @DisplayName("AC-178 署名の誤った通知は 401 で、配送状態を変えない")
        void sendsInvalidSignature() {
            // 準備：出荷済みの出荷と、署名を壊した配達完了の通知
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
            var invalid = TestCarrierNotifications.withInvalidSignature(
                    mockCarrier.notification(Carrier.YAMATO, shipment.trackingNumber(), "配達完了"));

            // 操作：送る
            var status = mockCarrier.send(baseUrl(), invalid);

            // 確認：401 で、出荷済みのまま
            assertThat(status).isEqualTo(401);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.SHIPPED);
        }

        @Test
        @DisplayName("AC-178 どの出荷とも合わない送り状番号の通知は 200 で、配送状態を変えない")
        void sendsUnknownTrackingNumber() {
            // 準備：出荷済みの出荷と、別の送り状番号の配達完了の通知
            var shipment = shipped(ShipmentSummary.Carrier.YAMATO);
            var unknown = mockCarrier.notification(Carrier.YAMATO, ShippingTestData.trackingNumber(), "配達完了");

            // 操作：送る
            var status = mockCarrier.send(baseUrl(), unknown);

            // 確認：200 で、出荷済みのまま
            assertThat(status).isEqualTo(200);
            assertThat(statusOf(shipment)).isEqualTo(ShipmentSummary.Status.SHIPPED);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 出荷の配送状態を読む。
     *
     * @param shipment 出荷
     * @return 配送状態
     */
    private ShipmentSummary.Status statusOf(Created shipment) {
        return shippingApi.find(List.of(shipment.orderId())).getFirst().status();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 作った出荷
     *
     * @param orderId        注文 ID
     * @param trackingNumber 送り状番号
     */
    private record Created(UUID orderId, String trackingNumber) {
    }

    /**
     * バックエンドの URL を返す。
     *
     * @return URL
     */
    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * 出荷を作る。
     *
     * <p>送り状番号は、ほかのテストが確定した出荷と重ならない。
     *
     * @param carrier 配送業者
     * @return 作った出荷
     */
    private Created shipped(ShipmentSummary.Carrier carrier) {
        var orderId = UUID.randomUUID();
        var trackingNumber = ShippingTestData.trackingNumber();
        createShipment(orderId, carrier, trackingNumber, SHIPPED_AT);
        return new Created(orderId, trackingNumber);
    }

    /**
     * mock の画面を開く。
     *
     * @return 画面の HTML
     */
    private String page() {
        return http.get().uri(baseUrl() + "/mock-carrier").retrieve().body(String.class);
    }

    /**
     * 画面のボタンを押す。
     *
     * @param shipment 出荷
     * @param status   ボタンの配送状態
     * @return 応答のステータス
     */
    private int press(Created shipment, String status) {
        return http.post().uri(baseUrl() + "/mock-carrier/orders/" + shipment.orderId() + "/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("status=" + status)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    /**
     * 出荷を、トランザクションの中で作る（出荷の作成は呼んだ側のトランザクションに加わる）。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param shippedAt      出荷日時
     */
    private void createShipment(UUID orderId, ShipmentSummary.Carrier carrier, String trackingNumber, Instant shippedAt) {
        transactionTemplate.executeWithoutResult(status -> shippingApi.create(orderId, carrier, trackingNumber, shippedAt));
    }
}
