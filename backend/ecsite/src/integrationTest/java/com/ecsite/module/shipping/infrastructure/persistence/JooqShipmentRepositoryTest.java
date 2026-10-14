package com.ecsite.module.shipping.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.Shipment;
import com.ecsite.module.shipping.domain.ShippingErrorCode;
import com.ecsite.module.shipping.domain.TrackingNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷のリポジトリの統合テスト（T9205、BR-19、23）。<br />
 * 各テストは巻き戻すので、データは残らない。
 */
@IntegrationTest
@Transactional
class JooqShipmentRepositoryTest {

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-15T01:00:00Z");

    @Autowired
    ShipmentRepository repository;

    @Test
    @DisplayName("BR-19 出荷を保存すると、注文 ID で読み直せる。配送状態は出荷済み、通知を反映した日時はまだない")
    void savesAndFindsByOrderId() {
        var orderId = UUID.randomUUID();
        var shipment = Shipment.create(UUID.randomUUID(), orderId, Carrier.YAMATO, TrackingNumber.of("1234-5678-9012"), SHIPPED_AT);

        repository.insert(shipment);

        assertThat(repository.findAllByOrderIds(List.of(orderId, UUID.randomUUID())))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.id()).isEqualTo(shipment.id());
                    assertThat(found.orderId()).isEqualTo(orderId);
                    assertThat(found.carrier()).isEqualTo(Carrier.YAMATO);
                    assertThat(found.trackingNumber().value()).isEqualTo("123456789012");
                    assertThat(found.status()).isEqualTo(DeliveryStatus.SHIPPED);
                    assertThat(found.shippedAt()).isEqualTo(SHIPPED_AT);
                    assertThat(found.lastNotifiedAt()).isEmpty();
                });
    }

    @Test
    @DisplayName("BR-23 同じ注文に出荷を重ねて保存すると、業務のエラーになる")
    void rejectsSecondShipmentForOrder() {
        var orderId = UUID.randomUUID();
        repository.insert(shipment(orderId, Carrier.YAMATO, "111122223333"));

        assertBusinessError(() -> repository.insert(shipment(orderId, Carrier.SAGAWA, "444455556666")),
                ShippingErrorCode.SHIPMENT_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("AC-174 配送業者と送り状番号で出荷を引ける。別の配送業者や別の番号は引けない")
    void findsByCarrierAndTrackingNumber() {
        var target = shipment(UUID.randomUUID(), Carrier.YAMATO, "111122223333");
        repository.insert(target);
        repository.insert(shipment(UUID.randomUUID(), Carrier.YAMATO, "999988887777"));

        assertThat(repository.findAllByTrackingForUpdate(Carrier.YAMATO, TrackingNumber.of("1111-2222-3333")))
                .extracting(Shipment::id).containsExactly(target.id());
        assertThat(repository.findAllByTrackingForUpdate(Carrier.SAGAWA, TrackingNumber.of("111122223333"))).isEmpty();
        assertThat(repository.findAllByTrackingForUpdate(Carrier.YAMATO, TrackingNumber.of("000000000000"))).isEmpty();
    }

    @Test
    @DisplayName("AC-174 進めた配送状態と通知を反映した日時を保存すると、読み直しても残る")
    void savesProgress() {
        var shipment = shipment(UUID.randomUUID(), Carrier.YAMATO, "111122223333");
        repository.insert(shipment);
        var notifiedAt = Instant.parse("2026-10-15T02:00:00Z");

        shipment.advanceTo(DeliveryStatus.DELIVERED, notifiedAt);
        repository.updateProgress(shipment);

        assertThat(repository.findAllByOrderIds(List.of(shipment.orderId())))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.status()).isEqualTo(DeliveryStatus.DELIVERED);
                    assertThat(found.lastNotifiedAt()).contains(notifiedAt);
                });
    }

    @Test
    @DisplayName("AC-189 配送状態が返送の出荷の注文 ID だけを返す")
    void findsReturnedOrderIds() {
        var returned = shipment(UUID.randomUUID(), Carrier.YAMATO, "111122223333");
        var delivered = shipment(UUID.randomUUID(), Carrier.YAMATO, "444455556666");
        var shipped = shipment(UUID.randomUUID(), Carrier.YAMATO, "777788889999");
        repository.insert(returned);
        repository.insert(delivered);
        repository.insert(shipped);
        returned.advanceTo(DeliveryStatus.RETURNED, SHIPPED_AT);
        delivered.advanceTo(DeliveryStatus.DELIVERED, SHIPPED_AT);
        repository.updateProgress(returned);
        repository.updateProgress(delivered);

        assertThat(repository.findReturnedOrderIds()).contains(returned.orderId())
                .doesNotContain(delivered.orderId(), shipped.orderId());
    }

    @Test
    @DisplayName("出荷を新しい順に、件数の上限まで読める")
    void findsRecentFirst() {
        var older = Shipment.create(UUID.randomUUID(), UUID.randomUUID(), Carrier.YAMATO, TrackingNumber.of("111"),
                Instant.parse("2090-01-01T00:00:00Z"));
        var newer = Shipment.create(UUID.randomUUID(), UUID.randomUUID(), Carrier.YAMATO, TrackingNumber.of("222"),
                Instant.parse("2090-01-02T00:00:00Z"));
        repository.insert(older);
        repository.insert(newer);

        assertThat(repository.findRecent(2)).extracting(Shipment::id).containsExactly(newer.id(), older.id());
        assertThat(repository.findRecent(1)).extracting(Shipment::id).containsExactly(newer.id());
    }

    /**
     * 出荷済みの出荷を作る。
     */
    private static Shipment shipment(UUID orderId, Carrier carrier, String trackingNumber) {
        return Shipment.create(UUID.randomUUID(), orderId, carrier, TrackingNumber.of(trackingNumber), SHIPPED_AT);
    }
}
