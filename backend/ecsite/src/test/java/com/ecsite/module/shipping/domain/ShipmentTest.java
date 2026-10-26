package com.ecsite.module.shipping.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷の単体テスト
 */
class ShipmentTest {

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

    @Test
    @DisplayName("BR-19 出荷は「出荷済み」で作られ、通知を反映した日時はまだない")
    void createsShipped() {
        var orderId = UUID.randomUUID();

        var shipment = Shipment.create(UUID.randomUUID(), orderId, Carrier.YAMATO, TrackingNumber.of("1234-5678-9012"), SHIPPED_AT);

        assertThat(shipment.status()).isEqualTo(DeliveryStatus.SHIPPED);
        assertThat(shipment.orderId()).isEqualTo(orderId);
        assertThat(shipment.carrier()).isEqualTo(Carrier.YAMATO);
        assertThat(shipment.trackingNumber().value()).isEqualTo("123456789012");
        assertThat(shipment.shippedAt()).isEqualTo(SHIPPED_AT);
        assertThat(shipment.lastNotifiedAt()).isEmpty();
    }

    @Test
    @DisplayName("AC-174 出荷済みから配送中、配達完了へ、通知のたびに進み、通知を反映した日時が更新される")
    void advancesToDelivered() {
        var shipment = shipped();

        assertThat(shipment.advanceTo(DeliveryStatus.IN_TRANSIT, FIRST)).isTrue();
        assertThat(shipment.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(shipment.lastNotifiedAt()).contains(FIRST);

        assertThat(shipment.advanceTo(DeliveryStatus.DELIVERED, SECOND)).isTrue();
        assertThat(shipment.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(shipment.lastNotifiedAt()).contains(SECOND);
    }

    @Test
    @DisplayName("AC-174 出荷済みから配送中、返送へ進む")
    void advancesToReturned() {
        var shipment = shipped();

        shipment.advanceTo(DeliveryStatus.IN_TRANSIT, FIRST);
        shipment.advanceTo(DeliveryStatus.RETURNED, SECOND);

        assertThat(shipment.status()).isEqualTo(DeliveryStatus.RETURNED);
    }

    @ParameterizedTest(name = "{0} の後に {1} が届いても {0} のまま")
    @CsvSource({
            "IN_TRANSIT, SHIPPED",
            "DELIVERED, IN_TRANSIT",
            "DELIVERED, SHIPPED",
            "RETURNED, IN_TRANSIT",
            "RETURNED, SHIPPED",
    })
    @DisplayName("AC-176 低い順位の通知が後から届いても、配送状態は戻らず、通知を反映した日時も変わらない")
    void doesNotGoBack(DeliveryStatus current, DeliveryStatus late) {
        var shipment = shipped();
        shipment.advanceTo(current, FIRST);

        var advanced = shipment.advanceTo(late, SECOND);

        assertThat(advanced).isFalse();
        assertThat(shipment.status()).isEqualTo(current);
        assertThat(shipment.lastNotifiedAt()).contains(FIRST);
    }

    @ParameterizedTest(name = "出荷済みから {0} へ直接進める")
    @CsvSource({"DELIVERED", "RETURNED"})
    @DisplayName("AC-177 途中の通知が届かなくても、出荷済みから後の状態へ進める")
    void skipsMissingStates(DeliveryStatus last) {
        var shipment = shipped();

        var advanced = shipment.advanceTo(last, FIRST);

        assertThat(advanced).isTrue();
        assertThat(shipment.status()).isEqualTo(last);
    }

    @Test
    @DisplayName("AC-177 出荷済みから配送中を飛ばして配達完了へ進む")
    void skipsInTransit() {
        var shipment = shipped();

        shipment.advanceTo(DeliveryStatus.DELIVERED, FIRST);

        assertThat(shipment.status()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @ParameterizedTest(name = "{0} の後に {1} は反映しない")
    @CsvSource({
            "DELIVERED, RETURNED",
            "RETURNED, DELIVERED",
    })
    @DisplayName("BR-19 配達完了と返送は入れ替わらない")
    void deliveredAndReturnedDoNotSwap(DeliveryStatus current, DeliveryStatus other) {
        var shipment = shipped();
        shipment.advanceTo(current, FIRST);

        var advanced = shipment.advanceTo(other, SECOND);

        assertThat(advanced).isFalse();
        assertThat(shipment.status()).isEqualTo(current);
    }

    @ParameterizedTest(name = "{0} の通知を重ねて反映しても 1 回だけ")
    @CsvSource({"IN_TRANSIT", "DELIVERED", "RETURNED"})
    @DisplayName("AC-175 同じ状態の通知が重ねて届いても、反映は 1 回だけで、通知を反映した日時は最初のまま")
    void appliesDuplicateOnce(DeliveryStatus status) {
        var shipment = shipped();

        var first = shipment.advanceTo(status, FIRST);
        var second = shipment.advanceTo(status, SECOND);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(shipment.status()).isEqualTo(status);
        assertThat(shipment.lastNotifiedAt()).contains(FIRST);
    }

    @Test
    @DisplayName("BR-19 出荷済みの通知は、出荷済みの出荷には反映しない（順位が上がらない）")
    void shippedNotificationDoesNothing() {
        var shipment = shipped();

        assertThat(shipment.advanceTo(DeliveryStatus.SHIPPED, FIRST)).isFalse();
        assertThat(shipment.lastNotifiedAt()).isEmpty();
    }

    /**
     * 出荷済みの出荷を作る。
     */
    private static Shipment shipped() {
        return Shipment.create(UUID.randomUUID(), UUID.randomUUID(), Carrier.YAMATO, TrackingNumber.of("123456789012"), SHIPPED_AT);
    }
}
