package com.ecsite.module.shipping.api;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.module.shipping.application.TrackingRequested;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.ShippingErrorCode;
import com.ecsite.module.shipping.infrastructure.mock.TestCarrierTracking;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static com.ecsite.jooq.shipping.Tables.SHIPMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 出荷の API の統合テスト。<br />
 * 出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID を使う。
 */
@IntegrationTest
class ShippingApiTest {

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-15T01:00:00Z");

    @Autowired
    ShippingApi shippingApi;

    @Autowired
    ShipmentRepository repository;

    /**
     * テスト用の物流サービスの追跡（mock を包む実装。呼ばれた時点のトランザクションの有無を記録するため、spy にする）
     */
    @MockitoSpyBean
    TestCarrierTracking tracking;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    @Test
    @DisplayName("BR-19 配送業者と送り状番号で出荷を作ると、配送状態は出荷済みで、送り状番号はハイフンを除いて保存される")
    void createsShipment() {
        var orderId = UUID.randomUUID();

        shippingApi.create(orderId, ShipmentSummary.Carrier.SAGAWA, " 1234-5678-9012 ", SHIPPED_AT);

        assertThat(shippingApi.find(List.of(orderId))).containsExactly(new ShipmentSummary(
                orderId, ShipmentSummary.Carrier.SAGAWA, "123456789012", ShipmentSummary.Status.SHIPPED, SHIPPED_AT));
    }

    @Test
    @DisplayName("BR-23 同じ注文に出荷を 2 回作ろうとすると、2 回目は業務のエラーになり、出荷は 1 つのまま")
    void doesNotCreateTwiceForOrder() {
        var orderId = UUID.randomUUID();
        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "111122223333", SHIPPED_AT);

        assertBusinessError(
                () -> shippingApi.create(orderId, ShipmentSummary.Carrier.SAGAWA, "444455556666", SHIPPED_AT),
                ShippingErrorCode.SHIPMENT_ALREADY_EXISTS);

        assertThat(dsl.fetchCount(SHIPMENT, SHIPMENT.ORDER_ID.eq(orderId))).isEqualTo(1);
        assertThat(shippingApi.find(List.of(orderId)))
                .singleElement()
                .satisfies(summary -> assertThat(summary.carrier()).isEqualTo(ShipmentSummary.Carrier.YAMATO));
    }

    @Test
    @DisplayName("形に合わない送り状番号では出荷を作れず、何も保存されない")
    void rejectsInvalidTrackingNumber() {
        var orderId = UUID.randomUUID();

        assertBusinessError(() -> shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "ABC", SHIPPED_AT),
                ShippingErrorCode.TRACKING_NUMBER_INVALID);

        assertThat(dsl.fetchCount(SHIPMENT, SHIPMENT.ORDER_ID.eq(orderId))).isZero();
    }

    @Test
    @DisplayName("AC-179 注文 ID でまとめて読むと、出荷のある注文の出荷だけを返す")
    void findsOnlyShippedOrders() {
        var shipped = UUID.randomUUID();
        var notShipped = UUID.randomUUID();
        shippingApi.create(shipped, ShipmentSummary.Carrier.JAPAN_POST, "5555", SHIPPED_AT);

        assertThat(shippingApi.find(List.of(shipped, notShipped))).extracting(ShipmentSummary::orderId)
                .containsExactly(shipped);
        assertThat(shippingApi.find(List.of())).isEmpty();
    }

    @Test
    @DisplayName("AC-189 配送状態が返送の出荷の注文 ID の一覧を返す")
    void listsReturnedOrders() {
        var returned = UUID.randomUUID();
        var inTransit = UUID.randomUUID();
        shippingApi.create(returned, ShipmentSummary.Carrier.YAMATO, "7771", SHIPPED_AT);
        shippingApi.create(inTransit, ShipmentSummary.Carrier.YAMATO, "7772", SHIPPED_AT);
        advance(returned, DeliveryStatus.RETURNED);
        advance(inTransit, DeliveryStatus.IN_TRANSIT);

        assertThat(shippingApi.returnedOrderIds()).contains(returned).doesNotContain(inTransit);
        assertThat(shippingApi.find(List.of(returned)))
                .singleElement()
                .satisfies(summary -> assertThat(summary.status()).isEqualTo(ShipmentSummary.Status.RETURNED));
    }

    @Test
    @DisplayName("AC-174 出荷を作ると、コミットの後に物流サービスで追跡が始まる。出荷を作れなかったときは始まらない")
    void startsTrackingAfterCommit() {
        var orderId = UUID.randomUUID();
        shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "8881-2223", SHIPPED_AT);
        assertBusinessError(() -> shippingApi.create(orderId, ShipmentSummary.Carrier.SAGAWA, "8884-5556", SHIPPED_AT),
                ShippingErrorCode.SHIPMENT_ALREADY_EXISTS);

        asyncEvents.awaitHandled(event -> event instanceof TrackingRequested requested
                && List.of("88812223", "88845556").contains(requested.trackingNumber()));
        assertThat(tracking.started()).contains(new TestCarrierTracking.Started(Carrier.YAMATO, "88812223"))
                .doesNotContain(new TestCarrierTracking.Started(Carrier.SAGAWA, "88845556"));
    }

    @Test
    @DisplayName("AC-306 追跡の開始のリスナーは、データベースのトランザクションの外で物流サービスを呼ぶ")
    void startsTrackingOutsideTransaction() {
        var transactionActive = new CopyOnWriteArrayList<Boolean>();
        doAnswer(invocation -> {
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(tracking).startTracking(any(), anyString());

        shippingApi.create(UUID.randomUUID(), ShipmentSummary.Carrier.YAMATO, "8891-2223", SHIPPED_AT);

        asyncEvents.awaitHandled(event -> event instanceof TrackingRequested requested
                && requested.trackingNumber().equals("88912223"));
        assertThat(transactionActive).containsExactly(false);
    }

    /**
     * 出荷の配送状態を進める（通知のエンドポイントを通さない、準備のための操作）。
     */
    private void advance(UUID orderId, DeliveryStatus status) {
        var shipment = repository.findAllByOrderIds(List.of(orderId)).getFirst();
        shipment.advanceTo(status, SHIPPED_AT);
        repository.updateProgress(shipment);
    }
}
