package com.ecsite.module.shipping.domain;

import com.ecsite.common.model.id.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷の単体テスト
 *
 * <p>出荷の作成と、物流サービスの通知による配送状態の遷移だけを確認する。通知の署名の確認と、出荷に一致しない通知を
 * 処理しないことは、通知の API の統合テストが確認する。
 */
class ShipmentTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-15T01:00:00Z");

    /**
     * 最初の通知を反映した日時
     */
    private static final Instant FIRST = Instant.parse("2026-10-15T02:00:00Z");

    /**
     * 次の通知を反映した日時
     */
    private static final Instant SECOND = Instant.parse("2026-10-15T03:00:00Z");

    /**
     * 入力された送り状番号（ハイフンを含む）
     */
    private static final String TRACKING_NUMBER_INPUT = "1234-5678-9012";

    /**
     * 保存する送り状番号（ハイフンを除いた値）
     */
    private static final String TRACKING_NUMBER = "123456789012";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("出荷の作成")
    class Creation {

        @Test
        @DisplayName("BR-19 出荷は「出荷済み」で作られる")
        void createsShipped() {
            // 操作：出荷を作る
            var shipment = Shipment.create(new ShipmentId(UUID.randomUUID()), new OrderId(UUID.randomUUID()), Carrier.YAMATO,
                    TrackingNumber.of(TRACKING_NUMBER_INPUT), SHIPPED_AT);

            // 確認：出荷済み
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("出荷は、注文、配送業者、送り状番号、出荷日時を持つ")
        void holdsShipment() {
            // 準備：注文 ID
            var orderId = new OrderId(UUID.randomUUID());

            // 操作：ハイフンを含む送り状番号で出荷を作る
            var shipment = Shipment.create(new ShipmentId(UUID.randomUUID()), orderId, Carrier.YAMATO,
                    TrackingNumber.of(TRACKING_NUMBER_INPUT), SHIPPED_AT);

            // 確認：渡した出荷の内容
            assertCreatedShipment(shipment, orderId);
        }

        @Test
        @DisplayName("BR-19 作った出荷には、通知を反映した日時がまだない")
        void hasNoNotificationYet() {
            // 操作：出荷を作る
            var shipment = shipped();

            // 確認：通知を反映した日時がない
            assertThat(shipment.lastNotifiedAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("通知による遷移")
    class Advance {

        @Test
        @DisplayName("AC-174 出荷済みの出荷は、配送中の通知で配送中に進み、通知を反映した日時を記録する")
        void advancesToInTransit() {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：配送中の通知を反映する
            var advanced = shipment.advanceTo(DeliveryStatus.IN_TRANSIT, FIRST, FIRST);

            // 確認：配送中で、反映した日時が最初の通知
            assertThat(advanced).isTrue();
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(shipment.lastNotifiedAt()).contains(FIRST);
        }

        @Test
        @DisplayName("AC-174 配送中の出荷は、配達完了の通知で配達完了に進み、通知を反映した日時を更新する")
        void advancesToDelivered() {
            // 準備：配送中の出荷
            var shipment = shipped();
            shipment.advanceTo(DeliveryStatus.IN_TRANSIT, FIRST, FIRST);

            // 操作：配達完了の通知を反映する
            var advanced = shipment.advanceTo(DeliveryStatus.DELIVERED, SECOND, SECOND);

            // 確認：配達完了で、反映した日時が次の通知
            assertThat(advanced).isTrue();
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(shipment.lastNotifiedAt()).contains(SECOND);
        }

        @Test
        @DisplayName("AC-174 配送中の出荷は、返送の通知で返送に進む")
        void advancesToReturned() {
            // 準備：配送中の出荷
            var shipment = shipped();
            shipment.advanceTo(DeliveryStatus.IN_TRANSIT, FIRST, FIRST);

            // 操作：返送の通知を反映する
            shipment.advanceTo(DeliveryStatus.RETURNED, SECOND, SECOND);

            // 確認：返送
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.RETURNED);
        }
    }

    @Nested
    @DisplayName("途中の通知が届かないとき")
    class Skip {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"DELIVERED", "RETURNED"})
        @DisplayName("AC-177 途中の通知が届かなくても、出荷済みから後の状態へ進める")
        void skipsMissingStates(DeliveryStatus last) {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：配送中を飛ばして、後の状態の通知を反映する
            var advanced = shipment.advanceTo(last, FIRST, FIRST);

            // 確認：後の状態
            assertThat(advanced).isTrue();
            assertThat(shipment.status()).isEqualTo(last);
        }

        @Test
        @DisplayName("AC-177 出荷済みから配送中を飛ばして配達完了へ進む")
        void skipsInTransit() {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：配達完了の通知を反映する
            shipment.advanceTo(DeliveryStatus.DELIVERED, FIRST, FIRST);

            // 確認：配達完了
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.DELIVERED);
        }
    }

    @Nested
    @DisplayName("順序の入れ替わった通知")
    class OutOfOrder {

        /**
         * 進んだ状態と、後から届く低い順位の通知の組を返す。
         *
         * @return 進んだ状態と、後から届く通知の状態
         */
        static Stream<Arguments> lateLowerNotifications() {
            return Stream.of(
                    Arguments.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.SHIPPED),
                    Arguments.of(DeliveryStatus.DELIVERED, DeliveryStatus.IN_TRANSIT),
                    Arguments.of(DeliveryStatus.DELIVERED, DeliveryStatus.SHIPPED),
                    Arguments.of(DeliveryStatus.RETURNED, DeliveryStatus.IN_TRANSIT),
                    Arguments.of(DeliveryStatus.RETURNED, DeliveryStatus.SHIPPED));
        }

        @ParameterizedTest(name = "「{0}」の後に「{1}」")
        @MethodSource("lateLowerNotifications")
        @DisplayName("AC-176 低い順位の通知が後から届いても、配送状態は戻らない")
        void doesNotGoBack(DeliveryStatus current, DeliveryStatus late) {
            // 準備：通知で進んだ出荷
            var shipment = shipped();
            shipment.advanceTo(current, FIRST, FIRST);

            // 操作：低い順位の通知を反映する
            var advanced = shipment.advanceTo(late, SECOND, SECOND);

            // 確認：反映せず、元の状態のまま
            assertThat(advanced).isFalse();
            assertThat(shipment.status()).isEqualTo(current);
        }

        @ParameterizedTest(name = "「{0}」の後に「{1}」")
        @MethodSource("lateLowerNotifications")
        @DisplayName("AC-176 低い順位の通知が後から届いても、通知を反映した日時は変わらない")
        void keepsLastNotifiedAt(DeliveryStatus current, DeliveryStatus late) {
            // 準備：通知で進んだ出荷
            var shipment = shipped();
            shipment.advanceTo(current, FIRST, FIRST);

            // 操作：低い順位の通知を反映する
            shipment.advanceTo(late, SECOND, SECOND);

            // 確認：最初の通知の日時のまま
            assertThat(shipment.lastNotifiedAt()).contains(FIRST);
        }

        @ParameterizedTest(name = "「{0}」の後に「{1}」")
        @CsvSource({
                "DELIVERED, RETURNED",
                "RETURNED, DELIVERED",
        })
        @DisplayName("BR-19 配達完了と返送は入れ替わらない")
        void deliveredAndReturnedDoNotSwap(DeliveryStatus current, DeliveryStatus other) {
            // 準備：配達完了か返送になった出荷
            var shipment = shipped();
            shipment.advanceTo(current, FIRST, FIRST);

            // 操作：もう一方の通知を反映する
            var advanced = shipment.advanceTo(other, SECOND, SECOND);

            // 確認：反映せず、元の状態のまま
            assertThat(advanced).isFalse();
            assertThat(shipment.status()).isEqualTo(current);
        }
    }

    @Nested
    @DisplayName("重複した通知")
    class Duplicate {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"IN_TRANSIT", "DELIVERED", "RETURNED"})
        @DisplayName("AC-175 同じ状態の通知が重ねて届いても、反映は 1 回だけ")
        void appliesDuplicateOnce(DeliveryStatus status) {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：同じ状態の通知を 2 回反映する
            var first = shipment.advanceTo(status, FIRST, FIRST);
            var second = shipment.advanceTo(status, SECOND, SECOND);

            // 確認：1 回目だけ反映する
            assertThat(first).isTrue();
            assertThat(second).isFalse();
            assertThat(shipment.status()).isEqualTo(status);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"IN_TRANSIT", "DELIVERED", "RETURNED"})
        @DisplayName("AC-175 同じ状態の通知が重ねて届いても、通知を反映した日時は最初のまま")
        void keepsFirstNotifiedAt(DeliveryStatus status) {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：同じ状態の通知を 2 回反映する
            shipment.advanceTo(status, FIRST, FIRST);
            shipment.advanceTo(status, SECOND, SECOND);

            // 確認：最初の通知の日時
            assertThat(shipment.lastNotifiedAt()).contains(FIRST);
        }

        @Test
        @DisplayName("BR-19 出荷済みの通知は、出荷済みの出荷には反映しない")
        void shippedNotificationDoesNothing() {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：出荷済みの通知を反映する
            var advanced = shipment.advanceTo(DeliveryStatus.SHIPPED, FIRST, FIRST);

            // 確認：順位が上がらないので反映せず、反映した日時もない
            assertThat(advanced).isFalse();
            assertThat(shipment.lastNotifiedAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("通知の発生日時")
    class OccurredAt {

        /**
         * 出荷日時より前の発生日時（1 ミリ秒前）
         */
        private static final Instant BEFORE_SHIPPED = SHIPPED_AT.minusMillis(1);

        @Test
        @DisplayName("通知の発生日時が出荷日時より前なら、配送状態は進まない")
        void doesNotAdvanceBeforeShipped() {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：出荷日時より前に起きた通知を反映する
            var advanced = shipment.advanceTo(DeliveryStatus.IN_TRANSIT, BEFORE_SHIPPED, FIRST);

            // 確認：反映せず、出荷済みのまま
            assertThat(advanced).isFalse();
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.SHIPPED);
        }

        @Test
        @DisplayName("通知の発生日時が出荷日時より前なら、通知を反映した日時は変わらない")
        void keepsLastNotifiedAtBeforeShipped() {
            // 準備：通知で進んだ出荷
            var shipment = shipped();
            shipment.advanceTo(DeliveryStatus.IN_TRANSIT, FIRST, FIRST);

            // 操作：出荷日時より前に起きた、順位の高い通知を反映する
            var advanced = shipment.advanceTo(DeliveryStatus.DELIVERED, BEFORE_SHIPPED, SECOND);

            // 確認：反映せず、最初の通知の日時のまま
            assertThat(advanced).isFalse();
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
            assertThat(shipment.lastNotifiedAt()).contains(FIRST);
        }

        @Test
        @DisplayName("通知の発生日時が出荷日時と同じなら、配送状態が進む")
        void advancesAtShippedAt() {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：出荷日時と同じ日時に起きた通知を反映する
            var advanced = shipment.advanceTo(DeliveryStatus.IN_TRANSIT, SHIPPED_AT, FIRST);

            // 確認：配送中に進む
            assertThat(advanced).isTrue();
            assertThat(shipment.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("通知を反映した日時は、通知の発生日時でなく、反映する日時になる")
        void recordsNotifiedAtNotOccurredAt() {
            // 準備：出荷済みの出荷
            var shipment = shipped();

            // 操作：発生日時と反映する日時が違う通知を反映する
            shipment.advanceTo(DeliveryStatus.IN_TRANSIT, FIRST, SECOND);

            // 確認：反映した日時は反映する日時
            assertThat(shipment.lastNotifiedAt()).contains(SECOND);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 作った出荷が、渡した内容を持つことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>注文 ID</li>
     *   <li>配送業者がヤマト運輸</li>
     *   <li>送り状番号がハイフンを除いた値</li>
     *   <li>出荷日時</li>
     * </ul>
     *
     * @param shipment 確認する出荷
     * @param orderId  注文 ID（実行ごとに作るので、定数にできない）
     */
    private static void assertCreatedShipment(Shipment shipment, OrderId orderId) {
        assertThat(shipment.orderId()).isEqualTo(orderId);
        assertThat(shipment.carrier()).isEqualTo(Carrier.YAMATO);
        assertThat(shipment.trackingNumber().value()).isEqualTo(TRACKING_NUMBER);
        assertThat(shipment.shippedAt()).isEqualTo(SHIPPED_AT);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 出荷済みの出荷を作る。
     *
     * @return 出荷
     */
    private static Shipment shipped() {
        return Shipment.create(new ShipmentId(UUID.randomUUID()), new OrderId(UUID.randomUUID()), Carrier.YAMATO, TrackingNumber.of(TRACKING_NUMBER), SHIPPED_AT);
    }
}
