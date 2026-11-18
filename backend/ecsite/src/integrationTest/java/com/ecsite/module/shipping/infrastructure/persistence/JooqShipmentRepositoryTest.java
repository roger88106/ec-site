package com.ecsite.module.shipping.infrastructure.persistence;

import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.Shipment;
import com.ecsite.module.shipping.domain.ShippingErrorCode;
import com.ecsite.module.shipping.domain.TrackingNumber;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.ShippingTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出荷のリポジトリの統合テスト
 *
 * <p>出荷の保存と再取得、配送業者と送り状番号での検索、配送状態の更新、返送の注文 ID の読み出しを確認する。
 *
 * <p>各テストは巻き戻すので、データは残らない。
 */
@IntegrationTest
@Transactional
class JooqShipmentRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-15T01:00:00Z");

    /**
     * 通知を反映した日時
     */
    private static final Instant NOTIFIED_AT = Instant.parse("2026-10-15T02:00:00Z");

    /**
     * ハイフンを含む、保存する出荷の送り状番号
     */
    private static final String HYPHENATED_TRACKING_NUMBER = "1234-5678-9012";

    /**
     * {@link #HYPHENATED_TRACKING_NUMBER} からハイフンを除いた、再取得した送り状番号
     */
    private static final String TRACKING_NUMBER_DIGITS = "123456789012";

    // ============================== Bean ==============================

    @Autowired
    ShipmentRepository repository;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("出荷の保存")
    class Insert {

        @Test
        @DisplayName("BR-19 出荷を保存すると、注文 ID で再取得でき、配送状態は出荷済みで、通知を反映した日時はまだない")
        void savesAndFindsByOrderId() {
            // 準備：ハイフンを含む送り状番号の出荷
            var orderId = UUID.randomUUID();
            var shipment = Shipment.create(UUID.randomUUID(), orderId, Carrier.YAMATO, TrackingNumber.of(HYPHENATED_TRACKING_NUMBER),
                    SHIPPED_AT);

            // 操作：保存する
            repository.insert(shipment);

            // 確認：出荷のない注文 ID と一緒に読んでも、保存した出荷だけが元の値で読める
            assertThat(repository.findAllByOrderIds(List.of(orderId, UUID.randomUUID())))
                    .singleElement()
                    .satisfies(found -> assertSavedShipment(found, shipment.id(), orderId));
        }

        @Test
        @DisplayName("BR-23 同じ注文に出荷を重ねて保存すると、業務のエラーになる")
        void rejectsSecondShipmentForOrder() {
            // 準備：出荷を保存する
            var orderId = UUID.randomUUID();
            repository.insert(shipment(orderId, Carrier.YAMATO, ShippingTestData.trackingNumber()));

            // 操作と確認：同じ注文の 2 つ目は、業務のエラー SHIPMENT_ALREADY_EXISTS
            assertBusinessError(() -> repository.insert(shipment(orderId, Carrier.SAGAWA, ShippingTestData.trackingNumber())),
                    ShippingErrorCode.SHIPMENT_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("配送業者と送り状番号での検索")
    class FindByTracking {

        @Test
        @DisplayName("AC-174 配送業者と送り状番号で、その出荷だけを引ける")
        void findsByCarrierAndTrackingNumber() {
            // 準備：引く出荷と、同じ配送業者で別の番号の出荷
            var digits = ShippingTestData.trackingNumber();
            var target = shipment(UUID.randomUUID(), Carrier.YAMATO, digits);
            repository.insert(target);
            repository.insert(shipment(UUID.randomUUID(), Carrier.YAMATO, ShippingTestData.trackingNumber()));

            // 操作と確認：引く出荷だけ
            assertThat(repository.findAllByTrackingForUpdate(Carrier.YAMATO, TrackingNumber.of(digits)))
                    .extracting(Shipment::id).containsExactly(target.id());
        }

        @Test
        @DisplayName("DC-548-8 同じ配送業者と送り状番号の出荷が複数あるとき、出荷 ID の昇順で返る")
        void returnsInShipmentIdOrder() {
            // 準備：出荷 ID が挿入の順と逆になるように、大きい ID から挿入する
            var digits = ShippingTestData.trackingNumber();
            var ids = List.of(
                    UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    UUID.fromString("00000000-0000-0000-0000-000000000002"));
            ids.forEach(id -> repository.insert(
                    Shipment.create(id, UUID.randomUUID(), Carrier.YAMATO, TrackingNumber.of(digits), SHIPPED_AT)));

            // 操作と確認：出荷 ID の昇順
            assertThat(repository.findAllByTrackingForUpdate(Carrier.YAMATO, TrackingNumber.of(digits)))
                    .extracting(Shipment::id)
                    .containsExactly(
                            UUID.fromString("00000000-0000-0000-0000-000000000001"),
                            UUID.fromString("00000000-0000-0000-0000-000000000002"),
                            UUID.fromString("00000000-0000-0000-0000-000000000003"));
        }

        @Test
        @DisplayName("AC-174 送り状番号が同じでも、別の配送業者では引けない")
        void doesNotFindByOtherCarrier() {
            // 準備：ヤマト運輸の出荷
            var digits = ShippingTestData.trackingNumber();
            repository.insert(shipment(UUID.randomUUID(), Carrier.YAMATO, digits));

            // 操作と確認：佐川急便では空
            assertThat(repository.findAllByTrackingForUpdate(Carrier.SAGAWA, TrackingNumber.of(digits))).isEmpty();
        }

        @Test
        @DisplayName("AC-174 保存していない送り状番号では引けない")
        void doesNotFindByOtherNumber() {
            // 準備：ヤマト運輸の出荷
            repository.insert(shipment(UUID.randomUUID(), Carrier.YAMATO, ShippingTestData.trackingNumber()));

            // 操作と確認：別の番号では空
            assertThat(repository.findAllByTrackingForUpdate(Carrier.YAMATO, TrackingNumber.of(ShippingTestData.trackingNumber())))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("配送状態の更新")
    class UpdateProgress {

        @Test
        @DisplayName("AC-174 進めた配送状態と通知を反映した日時を保存すると、再取得しても残る")
        void savesProgress() {
            // 準備：保存した出荷を、配達完了に進める
            var shipment = shipment(UUID.randomUUID(), Carrier.YAMATO, ShippingTestData.trackingNumber());
            repository.insert(shipment);
            shipment.advanceTo(DeliveryStatus.DELIVERED, NOTIFIED_AT, NOTIFIED_AT);

            // 操作：進めた出荷を保存する
            repository.updateProgress(List.of(shipment));

            // 確認：再取得すると、配達完了と反映した日時
            assertThat(repository.findAllByOrderIds(List.of(shipment.orderId())))
                    .singleElement()
                    .satisfies(found -> {
                        assertThat(found.status()).isEqualTo(DeliveryStatus.DELIVERED);
                        assertThat(found.lastNotifiedAt()).contains(NOTIFIED_AT);
                    });
        }
    }

    @Nested
    @DisplayName("返送の注文 ID")
    class ReturnedOrderIds {

        @Test
        @DisplayName("AC-189 配送状態が返送の出荷の注文 ID を返し、配達完了と出荷済みの注文 ID は返さない")
        void findsReturnedOrderIds() {
            // 準備：返送、配達完了、出荷済みの出荷
            var returned = shipment(UUID.randomUUID(), Carrier.YAMATO, ShippingTestData.trackingNumber());
            var delivered = shipment(UUID.randomUUID(), Carrier.YAMATO, ShippingTestData.trackingNumber());
            var shipped = shipment(UUID.randomUUID(), Carrier.YAMATO, ShippingTestData.trackingNumber());
            repository.insert(returned);
            repository.insert(delivered);
            repository.insert(shipped);
            returned.advanceTo(DeliveryStatus.RETURNED, SHIPPED_AT, SHIPPED_AT);
            delivered.advanceTo(DeliveryStatus.DELIVERED, SHIPPED_AT, SHIPPED_AT);
            repository.updateProgress(List.of(returned));
            repository.updateProgress(List.of(delivered));

            // 操作と確認：返送の注文 ID だけが入る（他のテストの返送も入りうるので、自分の 3 つだけを見る）
            assertThat(repository.findReturnedOrderIds()).contains(returned.orderId())
                    .doesNotContain(delivered.orderId(), shipped.orderId());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 再取得した出荷が、保存した出荷の値を持つことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>出荷 ID</li>
     *   <li>注文 ID</li>
     *   <li>配送業者がヤマト運輸</li>
     *   <li>送り状番号がハイフンを除いた数字</li>
     *   <li>配送状態が出荷済み</li>
     *   <li>出荷日時</li>
     *   <li>通知を反映した日時がない</li>
     * </ul>
     *
     * @param found      再取得した出荷
     * @param shipmentId 保存した出荷 ID（実行ごとに作るので、定数にできない）
     * @param orderId    保存した注文 ID（実行ごとに作るので、定数にできない）
     */
    private static void assertSavedShipment(Shipment found, UUID shipmentId, UUID orderId) {
        assertThat(found.id()).isEqualTo(shipmentId);
        assertThat(found.orderId()).isEqualTo(orderId);
        assertThat(found.carrier()).isEqualTo(Carrier.YAMATO);
        assertThat(found.trackingNumber().value()).isEqualTo(TRACKING_NUMBER_DIGITS);
        assertThat(found.status()).isEqualTo(DeliveryStatus.SHIPPED);
        assertThat(found.shippedAt()).isEqualTo(SHIPPED_AT);
        assertThat(found.lastNotifiedAt()).isEmpty();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 出荷済みの出荷を作る。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @return 出荷
     */
    private static Shipment shipment(UUID orderId, Carrier carrier, String trackingNumber) {
        return Shipment.create(UUID.randomUUID(), orderId, carrier, TrackingNumber.of(trackingNumber), SHIPPED_AT);
    }
}
