package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.ShippingTestData;
import com.ecsite.TestClockConfig;
import com.ecsite.module.shipping.api.ShipmentSummary;
import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 物流サービスの通知のエンドポイントの統合テスト。<br />
 * HTTP（MockMvc）を通し、署名はエンドポイントの実装を使わずにテストで作る。
 * 出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class CarrierWebhookTest {

    /**
     * 通知を受け付ける時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-15T02:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ShippingApi shippingApi;

    @Autowired
    ShipmentRepository repository;

    @Autowired
    MutableClock clock;

    /**
     * 通知の署名の鍵（統合テストの設定）
     */
    @Value("${ecsite.shipping.webhook-secret}")
    String secret;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    @ParameterizedTest(name = "ヤマト運輸の「{0}」は {1} になる")
    @CsvSource({
            "荷物受付, IN_TRANSIT",
            "発送, IN_TRANSIT",
            "作業店通過, IN_TRANSIT",
            "配達中, IN_TRANSIT",
            "持戻, IN_TRANSIT",
            "保管中, IN_TRANSIT",
            "配達完了, DELIVERED",
            "返品, RETURNED",
    })
    @DisplayName("AC-174 署名の正しい通知を受けると、状態の名前に応じて配送状態が進み、通知を反映した日時が入る")
    void advancesByNotification(String name, DeliveryStatus expected) throws Exception {
        var shipment = shipped();

        sendStatus(shipment, name).andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(expected);
        assertThat(lastNotifiedAtOf(shipment)).contains(NOW);
    }

    @Test
    @DisplayName("AC-174 ハイフン入りの送り状番号の通知でも、同じ出荷に反映される")
    void matchesHyphenatedTrackingNumber() throws Exception {
        var shipment = shipped();
        var hyphenated = shipment.trackingNumber().substring(0, 4) + "-" + shipment.trackingNumber().substring(4);

        send(signed(body("YAMATO", hyphenated, "配達中"))).andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("AC-175 同じ通知が重ねて届いても、1 回だけ反映され、通知を反映した日時は最初のまま")
    void appliesDuplicateOnce() throws Exception {
        var shipment = shipped();
        var notification = signed(body("YAMATO", shipment.trackingNumber(), "配達中"));

        send(notification).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(10));
        send(notification).andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(lastNotifiedAtOf(shipment)).contains(NOW);
    }

    @Test
    @DisplayName("AC-176 配達完了の後に配達中の通知が届いても、配達完了のまま")
    void doesNotGoBackWhenOutOfOrder() throws Exception {
        var shipment = shipped();
        sendStatus(shipment, "配達完了").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(10));

        sendStatus(shipment, "配達中").andExpect(status().isOk());
        sendStatus(shipment, "荷物受付").andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(lastNotifiedAtOf(shipment)).contains(NOW);
    }

    @Test
    @DisplayName("AC-177 途中の通知が届かなくても、出荷済みから配達完了へ進む")
    void skipsMissingNotifications() throws Exception {
        var shipment = shipped();

        sendStatus(shipment, "配達完了").andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("AC-177 途中の通知が届かなくても、出荷済みから返送へ進む")
    void skipsToReturned() throws Exception {
        var shipment = shipped();

        sendStatus(shipment, "返品").andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.RETURNED);
    }

    @ParameterizedTest(name = "{0} の後に {1} の通知が届いても {2} のまま")
    @CsvSource({"配達完了, 返品, DELIVERED", "返品, 配達完了, RETURNED"})
    @DisplayName("AC-176 配達完了と返送の通知が入れ替わっても、先に反映した状態のまま")
    void deliveredAndReturnedDoNotSwap(String first, String second, DeliveryStatus expected) throws Exception {
        var shipment = shipped();
        sendStatus(shipment, first).andExpect(status().isOk());

        sendStatus(shipment, second).andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-178 署名が合わない通知は 401 で処理せず、配送状態を変えない")
    void rejectsWrongSignature() throws Exception {
        var shipment = shipped();
        var body = body("YAMATO", shipment.trackingNumber(), "配達完了");

        send(new Request(body, "sha256=" + "0".repeat(64))).andExpect(status().isUnauthorized());
        send(new Request(body, sign(body, "別の鍵"))).andExpect(status().isUnauthorized());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    @DisplayName("AC-178 署名のない通知と、署名の後に本文を書き換えた通知は 401")
    void rejectsMissingOrTamperedSignature() throws Exception {
        var shipment = shipped();
        var signed = signed(body("YAMATO", shipment.trackingNumber(), "配達中"));
        var tampered = new Request(body("YAMATO", shipment.trackingNumber(), "配達完了"), signed.signature());

        send(new Request(signed.body(), null)).andExpect(status().isUnauthorized());
        send(tampered).andExpect(status().isUnauthorized());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    @DisplayName("AC-178 登録済みのどの出荷とも合わない送り状番号の通知は、200 で何も変えない")
    void ignoresUnknownTrackingNumber() throws Exception {
        var shipment = shipped();

        send(signed(body("YAMATO", ShippingTestData.trackingNumber(), "配達完了"))).andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    @DisplayName("AC-178 送り状番号が同じでも、配送業者が違う通知は、200 で何も変えない")
    void ignoresDifferentCarrier() throws Exception {
        var shipment = shipped();

        send(signed(body("SAGAWA", shipment.trackingNumber(), "配達完了"))).andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    @DisplayName("AC-178 対応の表にない状態の名前の通知は、200 で何も変えない")
    void ignoresUnknownStatusName() throws Exception {
        var shipment = shipped();

        sendStatus(shipment, "宇宙を移動中").andExpect(status().isOk());

        assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    @DisplayName("BR-22 署名が合っても、本文が読めない通知は 400")
    void rejectsUnreadableBody() throws Exception {
        send(signed("これは JSON ではない")).andExpect(status().isBadRequest());
        send(signed("{\"carrier\":\"YAMATO\"}")).andExpect(status().isBadRequest());
        send(signed("{\"carrier\":\"UNKNOWN\",\"trackingNumber\":\"1\",\"status\":\"配達中\","
                + "\"occurredAt\":\"2026-10-15T01:00:00Z\"}")).andExpect(status().isBadRequest());
    }

// ============================== 補助 ==============================

    /**
     * 署名付きの要求（本文と、ヘッダーに入れる署名）
     */
    private record Request(String body, String signature) {
    }

    /**
     * 作った出荷
     */
    private record Created(UUID orderId, String trackingNumber) {
    }

    /**
     * ヤマト運輸の出荷を作る。送り状番号は、ほかのテストが確定した出荷と重ならない。
     */
    private Created shipped() {
        var orderId = UUID.randomUUID();
        var trackingNumber = ShippingTestData.trackingNumber();
        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, trackingNumber, NOW);
        return new Created(orderId, trackingNumber);
    }

    /**
     * 出荷の送り状番号と状態の名前で、署名付きの通知を送る。
     */
    private ResultActions sendStatus(Created shipment, String statusName) throws Exception {
        return send(signed(body("YAMATO", shipment.trackingNumber(), statusName)));
    }

    /**
     * 通知の本文を作る。
     */
    private static String body(String carrier, String trackingNumber, String statusName) {
        return "{\"carrier\":\"%s\",\"trackingNumber\":\"%s\",\"status\":\"%s\",\"occurredAt\":\"2026-10-15T01:30:00Z\"}"
                .formatted(carrier, trackingNumber, statusName);
    }

    /**
     * 本文に正しい鍵で署名する。
     */
    private Request signed(String body) {
        return new Request(body, sign(body, secret));
    }

    /**
     * 本文の HMAC-SHA256（16 進、小文字）を、ヘッダーの値の形にする。
     */
    private static String sign(String body, String key) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 通知のエンドポイントへ送る。
     */
    private ResultActions send(Request request) throws Exception {
        var builder = post("/api/webhooks/carrier")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.body().getBytes(StandardCharsets.UTF_8));
        if (request.signature() != null) {
            builder.header("X-Carrier-Signature", request.signature());
        }
        return mockMvc.perform(builder);
    }

    /**
     * 出荷の配送状態を読む。
     */
    private DeliveryStatus statusOf(Created shipment) {
        return repository.findAllByOrderIds(List.of(shipment.orderId())).getFirst().status();
    }

    /**
     * 出荷の、最後に通知を反映した日時を読む。
     */
    private Optional<Instant> lastNotifiedAtOf(Created shipment) {
        return repository.findAllByOrderIds(List.of(shipment.orderId())).getFirst().lastNotifiedAt();
    }
}
