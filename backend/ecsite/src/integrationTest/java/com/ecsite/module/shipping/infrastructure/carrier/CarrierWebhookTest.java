package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import com.ecsite.support.MutableClock;
import com.ecsite.support.async.Concurrently;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.ShippingTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 物流サービスの通知のエンドポイントの統合テスト
 *
 * <p>通知で配送状態が進むこと、重ねて届いた通知・順序の入れ替わり・途中の欠けの扱い、署名の誤りと合わない通知の扱いを確認する。
 * 配送状態の変化のイベントは {@code DeliveryStatusChangedEventTest} が確認する。
 *
 * <p>HTTP（MockMvc）を通し、署名はエンドポイントの実装を使わずにテストで作る。
 * 出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
@RecordApplicationEvents
class CarrierWebhookTest {

    // ============================== 定数 ==============================

    /**
     * 通知を受け付ける時刻
     *
     * <p>日本時間の 2026-10-15 11:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-15T02:00:00Z");

    /**
     * 正しくない署名（形は合うが、値がどの本文の署名とも合わない）
     */
    private static final String ZERO_SIGNATURE = "sha256=" + "0".repeat(64);

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ShippingApi shippingApi;

    @Autowired
    ShipmentRepository repository;

    @Autowired
    MutableClock clock;

    /**
     * 発行されたイベント
     */
    @Autowired
    ApplicationEvents events;

    /**
     * 通知の署名の鍵（統合テストの設定）
     */
    @Value("${ecsite.shipping.webhook-secret}")
    String secret;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("通知による配送状態の遷移")
    class Advance {

        @ParameterizedTest(name = "「{0}」")
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
        @DisplayName("AC-174 署名の正しい通知を受けると、ヤマト運輸の状態の名前に応じた配送状態に進み、通知を反映した日時が入る")
        void advancesByNotification(String name, DeliveryStatus expected) throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：状態の名前の通知を送る
            sendStatus(shipment, name).andExpect(status().isOk());

            // 確認：対応の表の配送状態と、受け付けた時刻
            assertThat(statusOf(shipment)).isEqualTo(expected);
            assertThat(lastNotifiedAtOf(shipment)).contains(NOW);
        }

        @Test
        @DisplayName("AC-174 ハイフン入りの送り状番号の通知でも、同じ出荷に反映される")
        void matchesHyphenatedTrackingNumber() throws Exception {
            // 準備：出荷済みの出荷と、その送り状番号にハイフンを入れた番号
            var shipment = shipped();
            var hyphenated = shipment.trackingNumber().substring(0, 4) + "-" + shipment.trackingNumber().substring(4);

            // 操作：ハイフン入りの番号で配達中を送る
            send(signed(body("YAMATO", hyphenated, "配達中"))).andExpect(status().isOk());

            // 確認：配送中になる
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.IN_TRANSIT);
        }
    }

    @Nested
    @DisplayName("重複・順序の入れ替わり・途中の欠け")
    class Disorder {

        @Test
        @DisplayName("AC-175 同じ通知が重ねて届いても、1 回だけ反映され、通知を反映した日時は最初のまま")
        void appliesDuplicateOnce() throws Exception {
            // 準備：出荷済みの出荷と、配達中の通知
            var shipment = shipped();
            var notification = signed(body("YAMATO", shipment.trackingNumber(), "配達中"));

            // 操作：同じ通知を、10 分空けて 2 回送る
            send(notification).andExpect(status().isOk());
            clock.advance(Duration.ofMinutes(10));
            send(notification).andExpect(status().isOk());

            // 確認：配送中で、反映した日時は 1 回目
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(lastNotifiedAtOf(shipment)).contains(NOW);
        }

        @Test
        @DisplayName("AC-176 配達完了の後に、配達中や荷物受付の通知が届いても、配達完了のまま")
        void doesNotGoBackWhenOutOfOrder() throws Exception {
            // 準備：配達完了にした出荷
            var shipment = shipped();
            sendStatus(shipment, "配達完了").andExpect(status().isOk());
            clock.advance(Duration.ofMinutes(10));

            // 操作：10 分後に、順位の低い通知を送る
            sendStatus(shipment, "配達中").andExpect(status().isOk());
            sendStatus(shipment, "荷物受付").andExpect(status().isOk());

            // 確認：配達完了のままで、反映した日時も配達完了のとき
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(lastNotifiedAtOf(shipment)).contains(NOW);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"配達完了, 返品, DELIVERED", "返品, 配達完了, RETURNED"})
        @DisplayName("AC-176 配達完了と返送の通知が入れ替わって届いても、先に反映した状態のまま")
        void deliveredAndReturnedDoNotSwap(String first, String second, DeliveryStatus expected) throws Exception {
            // 準備：先の通知を反映した出荷
            var shipment = shipped();
            sendStatus(shipment, first).andExpect(status().isOk());

            // 操作：同じ順位の、もう一方の通知を送る
            sendStatus(shipment, second).andExpect(status().isOk());

            // 確認：先に反映した状態のまま
            assertThat(statusOf(shipment)).isEqualTo(expected);
        }

        @Test
        @DisplayName("AC-176 同じ出荷へ配達中と配達完了の通知が同時に届いても、順位の高い配達完了が残る")
        void keepsHigherStatusWhenNotifiedConcurrently() throws Exception {
            // 準備：出荷済みの出荷と、配達中・配達完了の通知
            var shipment = shipped();
            var notifications = List.of(
                    signed(body("YAMATO", shipment.trackingNumber(), "配達中")),
                    signed(body("YAMATO", shipment.trackingNumber(), "配達完了")));

            // 操作：2 つの通知を同時に送る
            var statuses = Concurrently.run(notifications.size(),
                    i -> send(notifications.get(i)).andReturn().getResponse().getStatus());

            // 確認：どちらも受け付け、配達完了で終わる
            assertThat(statuses).containsOnly(200);
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("AC-177 途中の通知が届かなくても、出荷済みから配達完了へ進む")
        void skipsToDelivered() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：配送中を経ずに、配達完了を送る
            sendStatus(shipment, "配達完了").andExpect(status().isOk());

            // 確認：配達完了になる
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("AC-177 途中の通知が届かなくても、出荷済みから返送へ進む")
        void skipsToReturned() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：配送中を経ずに、返品を送る
            sendStatus(shipment, "返品").andExpect(status().isOk());

            // 確認：返送になる
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.RETURNED);
        }
    }

    @Nested
    @DisplayName("同じ送り状番号の複数の出荷")
    class SharedTrackingNumber {

        @Test
        @DisplayName("AC-174 同じ配送業者・送り状番号の出荷が 2 つあれば、1 回の通知で両方が進み、注文ごとに配送状態の変化のイベントが出る")
        void advancesAllShipmentsWithSameTrackingNumber() throws Exception {
            // 準備：同じ送り状番号の、別の注文の出荷を 2 つ
            var first = shipped();
            var second = new Created(UUID.randomUUID(), first.trackingNumber());
            shippingApi.create(second.orderId(), ShipmentSummary.Carrier.YAMATO, second.trackingNumber(), NOW);

            // 操作：配達中を 1 回送る
            sendStatus(first, "配達中").andExpect(status().isOk());

            // 確認：両方が配送中になり、注文ごとに出荷済みから配送中へのイベントが 1 件ずつ
            assertThat(statusOf(first)).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(statusOf(second)).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(events.stream(DeliveryStatusChanged.class)
                    .filter(event -> event.orderId().equals(first.orderId()) || event.orderId().equals(second.orderId()))
                    .filter(event -> event.to() == DeliveryStatusChanged.Status.IN_TRANSIT))
                    .extracting(DeliveryStatusChanged::orderId, DeliveryStatusChanged::from)
                    .containsExactlyInAnyOrder(
                            tuple(first.orderId(), DeliveryStatusChanged.Status.SHIPPED),
                            tuple(second.orderId(), DeliveryStatusChanged.Status.SHIPPED));
        }
    }

    @Nested
    @DisplayName("通知の発生日時と出荷日時")
    class OccurredAtAndShippedAt {

        @Test
        @DisplayName("DC-548-1 発生日時が出荷日時より前の通知は 200 で、配送状態も最後に通知を反映した日時も変えず、イベントも出ない")
        void ignoresNotificationBeforeShippedAt() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：出荷日時の 1ms 前に起きた配達中の通知を送る
            send(signed(body("YAMATO", shipment.trackingNumber(), "配達中", NOW.minusMillis(1)))).andExpect(status().isOk());

            // 確認：出荷済みのままで、反映した日時は空。イベントは出荷時の 1 件だけ
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
            assertThat(lastNotifiedAtOf(shipment)).isEmpty();
            assertThat(events.stream(DeliveryStatusChanged.class).filter(event -> event.orderId().equals(shipment.orderId())))
                    .extracting(DeliveryStatusChanged::to).containsExactly(DeliveryStatusChanged.Status.SHIPPED);
        }

        @Test
        @DisplayName("DC-548-1 発生日時が出荷日時と同じ通知は反映される")
        void appliesNotificationAtShippedAt() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：出荷日時と同じ発生日時の配達中の通知を送る
            send(signed(body("YAMATO", shipment.trackingNumber(), "配達中", NOW))).andExpect(status().isOk());

            // 確認：配送中になる
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("DC-548-2 送り状番号が同じ 2 つの出荷のうち、出荷日時が発生日時以前の出荷だけが進む")
        void advancesOnlyShipmentShippedBeforeOccurredAt() throws Exception {
            // 準備：同じ送り状番号の、出荷日時が 1 時間違う出荷（古い出荷と、送り状番号を再利用した新しい出荷）
            var older = shipped();
            var newer = new Created(UUID.randomUUID(), older.trackingNumber());
            shippingApi.create(newer.orderId(), ShipmentSummary.Carrier.YAMATO, newer.trackingNumber(), NOW.plus(Duration.ofHours(1)));
            clock.setInstant(NOW.plus(Duration.ofHours(1)));

            // 操作：2 つの出荷日時の間に起きた配達中の通知を送る
            send(signed(body("YAMATO", older.trackingNumber(), "配達中", NOW.plus(Duration.ofMinutes(30))))).andExpect(status().isOk());

            // 確認：出荷日時が前の出荷だけが配送中になる
            assertThat(statusOf(older)).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(statusOf(newer)).isEqualTo(DeliveryStatus.SHIPPED);
        }
    }

    @Nested
    @DisplayName("範囲の外の発生日時")
    class OccurredAtOutOfRange {

        /**
         * 古さの上限（既定）
         */
        private static final Duration MAX_AGE = Duration.ofDays(30);

        /**
         * 未来の許容（既定）
         */
        private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

        @Test
        @DisplayName("DC-548-3 発生日時が古さの上限ちょうど前の通知は 400 で処理せず、配送状態を変えない")
        void rejectsOccurredAtAtMaxAge() throws Exception {
            // 準備：古さの上限より前に出荷した出荷
            var shipment = shippedAt(NOW.minus(MAX_AGE).minus(Duration.ofDays(1)));

            // 操作と確認：現在時刻の 30 日前ちょうどの通知は 400
            send(signed(body("YAMATO", shipment.trackingNumber(), "配達中", NOW.minus(MAX_AGE)))).andExpect(status().isBadRequest());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("DC-548-3 発生日時が古さの上限のすぐ内側（1ms 後）の通知は受け付ける")
        void acceptsOccurredAtJustInsideMaxAge() throws Exception {
            // 準備：古さの上限より前に出荷した出荷
            var shipment = shippedAt(NOW.minus(MAX_AGE).minus(Duration.ofDays(1)));

            // 操作と確認：30 日前の 1ms 後の通知は 200
            send(signed(body("YAMATO", shipment.trackingNumber(), "配達中", NOW.minus(MAX_AGE).plusMillis(1))))
                    .andExpect(status().isOk());

            // 確認：配送中になる
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("DC-548-3 発生日時が未来の許容を超えて先の通知は 400 で処理せず、配送状態を変えない")
        void rejectsOccurredAtBeyondFutureTolerance() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作と確認：5 分の 1ms 先の通知は 400
            send(signed(body("YAMATO", shipment.trackingNumber(), "配達中", NOW.plus(FUTURE_TOLERANCE).plusMillis(1))))
                    .andExpect(status().isBadRequest());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("DC-548-3 発生日時が未来の許容ちょうど先の通知は受け付ける")
        void acceptsOccurredAtAtFutureTolerance() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作と確認：5 分先ちょうどの通知は 200
            send(signed(body("YAMATO", shipment.trackingNumber(), "配達中", NOW.plus(FUTURE_TOLERANCE)))).andExpect(status().isOk());

            // 確認：配送中になる
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.IN_TRANSIT);
        }
    }

    @Nested
    @DisplayName("通知を反映した日時")
    class LastNotifiedAt {

        @Test
        @DisplayName("DC-548-6 最後に通知を反映した日時は、通知の発生日時でなく、処理した時刻になる")
        void usesProcessingTime() throws Exception {
            // 準備：出荷済みの出荷と、10 分進めた時計
            var shipment = shipped();
            var processedAt = NOW.plus(Duration.ofMinutes(10));
            clock.setInstant(processedAt);

            // 操作：8 分前（出荷の 2 分後）に起きた配達中の通知を送る
            send(signed(body("YAMATO", shipment.trackingNumber(), "配達中", NOW.plus(Duration.ofMinutes(2))))).andExpect(status().isOk());

            // 確認：処理した時刻が入る
            assertThat(lastNotifiedAtOf(shipment)).contains(processedAt);
        }
    }

    @Nested
    @DisplayName("署名を確認できない通知")
    class Signature {

        @Test
        @DisplayName("AC-178 署名の値が合わない通知は 401 で処理せず、配送状態を変えない")
        void rejectsWrongSignatureValue() throws Exception {
            // 準備：出荷済みの出荷と、配達完了の本文
            var shipment = shipped();
            var body = body("YAMATO", shipment.trackingNumber(), "配達完了");

            // 操作と確認：形だけ合う署名で送ると 401
            send(new Request(body, ZERO_SIGNATURE)).andExpect(status().isUnauthorized());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("AC-178 別の鍵で署名した通知は 401 で処理せず、配送状態を変えない")
        void rejectsSignatureWithOtherKey() throws Exception {
            // 準備：出荷済みの出荷と、配達完了の本文
            var shipment = shipped();
            var body = body("YAMATO", shipment.trackingNumber(), "配達完了");

            // 操作と確認：別の鍵の署名で送ると 401
            send(new Request(body, sign(body, "別の鍵"))).andExpect(status().isUnauthorized());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("AC-178 署名のない通知は 401 で処理せず、配送状態を変えない")
        void rejectsMissingSignature() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作と確認：署名のヘッダーなしで送ると 401
            send(new Request(body("YAMATO", shipment.trackingNumber(), "配達中"), null)).andExpect(status().isUnauthorized());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("AC-178 署名の後に本文を書き換えた通知は 401 で処理せず、配送状態を変えない")
        void rejectsTamperedBody() throws Exception {
            // 準備：出荷済みの出荷と、配達中の本文の署名を付けた、配達完了の本文
            var shipment = shipped();
            var signed = signed(body("YAMATO", shipment.trackingNumber(), "配達中"));
            var tampered = new Request(body("YAMATO", shipment.trackingNumber(), "配達完了"), signed.signature());

            // 操作と確認：401
            send(tampered).andExpect(status().isUnauthorized());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }
    }

    @Nested
    @DisplayName("どの出荷とも合わない通知")
    class Unmatched {

        @Test
        @DisplayName("AC-178 登録済みのどの出荷とも合わない送り状番号の通知は、200 で何も変えない")
        void ignoresUnknownTrackingNumber() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作と確認：別の送り状番号の通知は 200
            send(signed(body("YAMATO", ShippingTestData.trackingNumber(), "配達完了"))).andExpect(status().isOk());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"ABC", "１２３４５６７８９０１２", "1234/5678"})
        @DisplayName("AC-178 送り状番号の形に合わない通知は、200 で何も変えない")
        void ignoresMalformedTrackingNumber(String trackingNumber) throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作と確認：形に合わない番号の通知は 200
            send(signed(body("YAMATO", trackingNumber, "配達完了"))).andExpect(status().isOk());

            // 確認：出荷済みのまま、この注文の配送状態の変化のイベントは出荷のときの 1 件だけ
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
            assertThat(events.stream(DeliveryStatusChanged.class).filter(event -> event.orderId().equals(shipment.orderId())))
                    .extracting(DeliveryStatusChanged::to).containsExactly(DeliveryStatusChanged.Status.SHIPPED);
        }

        @Test
        @DisplayName("AC-178 送り状番号が同じでも、配送業者が違う通知は、200 で何も変えない")
        void ignoresDifferentCarrier() throws Exception {
            // 準備：ヤマト運輸の出荷
            var shipment = shipped();

            // 操作と確認：同じ番号の佐川急便の通知は 200
            send(signed(body("SAGAWA", shipment.trackingNumber(), "配達完了"))).andExpect(status().isOk());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("AC-178 対応の表にない状態の名前の通知は、200 で何も変えない")
        void ignoresUnknownStatusName() throws Exception {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作と確認：表にない名前の通知は 200
            sendStatus(shipment, "宇宙を移動中").andExpect(status().isOk());

            // 確認：出荷済みのまま
            assertThat(statusOf(shipment)).isEqualTo(DeliveryStatus.SHIPPED);
        }
    }

    @Nested
    @DisplayName("読めない本文")
    class UnreadableBody {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {
                "これは JSON ではない",
                "{\"carrier\":\"YAMATO\"}",
                "{\"carrier\":\"UNKNOWN\",\"trackingNumber\":\"1\",\"status\":\"配達中\",\"occurredAt\":\"2026-10-15T01:00:00Z\"}",
        })
        @DisplayName("BR-22 署名が合っても、本文が読めない通知は 400")
        void rejectsUnreadableBody(String body) throws Exception {
            // 操作と確認：正しい鍵で署名して送ると 400
            send(signed(body)).andExpect(status().isBadRequest());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 出荷の配送状態を読む。
     *
     * @param shipment 出荷
     * @return 配送状態
     */
    private DeliveryStatus statusOf(Created shipment) {
        return repository.findAllByOrderIds(List.of(shipment.orderId())).getFirst().status();
    }

    /**
     * 出荷の、最後に通知を反映した日時を読む。
     *
     * @param shipment 出荷
     * @return 日時。まだ反映していなければ空
     */
    private Optional<Instant> lastNotifiedAtOf(Created shipment) {
        return repository.findAllByOrderIds(List.of(shipment.orderId())).getFirst().lastNotifiedAt();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 署名付きのリクエスト
     *
     * @param body      本文
     * @param signature ヘッダーに入れる署名（入れないときは null）
     */
    private record Request(String body, String signature) {
    }

    /**
     * 作った出荷
     *
     * @param orderId        注文 ID
     * @param trackingNumber 送り状番号
     */
    private record Created(UUID orderId, String trackingNumber) {
    }

    /**
     * ヤマト運輸の出荷を作る。
     *
     * <p>送り状番号は、ほかのテストが確定した出荷と重ならない。
     *
     * @return 作った出荷
     */
    private Created shipped() {
        return shippedAt(NOW);
    }

    /**
     * 出荷日時を指定して、ヤマト運輸の出荷を作る。
     *
     * @param shippedAt 出荷日時
     * @return 作った出荷
     */
    private Created shippedAt(Instant shippedAt) {
        var orderId = UUID.randomUUID();
        var trackingNumber = ShippingTestData.trackingNumber();
        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, trackingNumber, shippedAt);
        return new Created(orderId, trackingNumber);
    }

    /**
     * 出荷の送り状番号と状態の名前で、署名付きの通知を送る。
     *
     * @param shipment   出荷
     * @param statusName 物流サービスの状態の名前
     * @return 応答
     * @throws Exception 送れなかったとき
     */
    private ResultActions sendStatus(Created shipment, String statusName) throws Exception {
        return send(signed(body("YAMATO", shipment.trackingNumber(), statusName)));
    }

    /**
     * 通知の本文を作る。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param statusName     物流サービスの状態の名前
     * @return 本文の JSON
     */
    private static String body(String carrier, String trackingNumber, String statusName) {
        return body(carrier, trackingNumber, statusName, NOW.plus(Duration.ofMinutes(3)));
    }

    /**
     * 発生日時を指定して、通知の本文を作る。
     *
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param statusName     物流サービスの状態の名前
     * @param occurredAt     発生日時
     * @return 本文の JSON
     */
    private static String body(String carrier, String trackingNumber, String statusName, Instant occurredAt) {
        return "{\"carrier\":\"%s\",\"trackingNumber\":\"%s\",\"status\":\"%s\",\"occurredAt\":\"%s\"}"
                .formatted(carrier, trackingNumber, statusName, occurredAt);
    }

    /**
     * 本文に正しい鍵で署名する。
     *
     * @param body 本文
     * @return 署名付きのリクエスト
     */
    private Request signed(String body) {
        return new Request(body, sign(body, secret));
    }

    /**
     * 本文の HMAC-SHA256（16 進、小文字）を、ヘッダーの値の形にする。
     *
     * @param body 本文
     * @param key  鍵
     * @return ヘッダーの値
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
     *
     * @param request 署名付きのリクエスト
     * @return 応答
     * @throws Exception 送れなかったとき
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
}
