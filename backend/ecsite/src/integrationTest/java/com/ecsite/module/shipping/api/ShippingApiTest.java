package com.ecsite.module.shipping.api;

import com.ecsite.module.shipping.api.model.ShipmentSummary;
import com.ecsite.module.shipping.application.TrackingRequests;
import com.ecsite.module.shipping.application.port.ShipmentRepository;
import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.domain.DeliveryStatus;
import com.ecsite.module.shipping.domain.ShippingErrorCode;
import com.ecsite.module.shipping.infrastructure.mock.TestCarrierTracking;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.jooq.shipping.Tables.SHIPMENT;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * 出荷の API の統合テスト
 *
 * <p>出荷の作成、注文 ID での読み出し、返送の注文の一覧と、作成の後の追跡の開始を確認する。
 * 通知で配送状態が進むことは {@code CarrierWebhookTest} が確認する。
 *
 * <p>出荷はトランザクションを確定させて保存するので、テストごとに別の注文 ID と送り状番号を使う。
 */
@IntegrationTest
class ShippingApiTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-15T01:00:00Z");

    /**
     * 前後に空白があり、ハイフンを含む、入力の送り状番号
     */
    private static final String TRACKING_NUMBER_INPUT = " 1234-5678-9012 ";

    /**
     * {@link #TRACKING_NUMBER_INPUT} から空白とハイフンを除いた、保存される送り状番号
     */
    private static final String TRACKING_NUMBER_DIGITS = "123456789012";

    // ============================== Bean ==============================

    @Autowired
    ShippingApi shippingApi;

    @Autowired
    ShipmentRepository repository;

    /**
     * テスト用の物流サービスの追跡
     *
     * <p>mock を包む実装。呼ばれた時点のトランザクションの有無を記録するため、spy にする。
     */
    @MockitoSpyBean
    TestCarrierTracking tracking;

    @Autowired
    DSLContext dsl;

    @Autowired
    AsyncEvents asyncEvents;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("出荷の作成")
    class Create {

        @Test
        @DisplayName("BR-19 作った出荷は、指定した配送業者と出荷日時を持ち、配送状態は出荷済み、送り状番号は空白とハイフンを除いた数字になる")
        void createsShipment() {
            // 操作：空白とハイフンを含む送り状番号で出荷を作る
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.SAGAWA, TRACKING_NUMBER_INPUT, SHIPPED_AT);

            // 確認：読み出した出荷の全ての項目
            assertThat(shippingApi.find(List.of(orderId))).containsExactly(new ShipmentSummary(
                    orderId, ShipmentSummary.Carrier.SAGAWA, TRACKING_NUMBER_DIGITS, ShipmentSummary.Status.SHIPPED, SHIPPED_AT));
        }

        @Test
        @DisplayName("BR-23 同じ注文に出荷を 2 回作ろうとすると、2 回目は業務のエラーになる")
        void rejectsSecondShipment() {
            // 準備：出荷を作る
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "111122223333", SHIPPED_AT);

            // 操作と確認：業務のエラー SHIPMENT_ALREADY_EXISTS で拒否される
            assertBusinessError(
                    () -> shippingApi.create(orderId, ShipmentSummary.Carrier.SAGAWA, "444455556666", SHIPPED_AT),
                    ShippingErrorCode.SHIPMENT_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("BR-23 同じ注文に 2 回目の出荷を作ろうとしても、出荷は最初の 1 つのまま")
        void keepsFirstShipment() {
            // 準備：出荷を作る
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "111122224444", SHIPPED_AT);

            // 操作：別の配送業者で、もう一度作ろうとする（業務のエラーになる）
            assertBusinessError(
                    () -> shippingApi.create(orderId, ShipmentSummary.Carrier.SAGAWA, "444455557777", SHIPPED_AT),
                    ShippingErrorCode.SHIPMENT_ALREADY_EXISTS);

            // 確認：行は 1 つで、最初の配送業者のまま
            assertThat(dsl.fetchCount(SHIPMENT, SHIPMENT.ORDER_ID.eq(orderId))).isEqualTo(1);
            assertThat(shippingApi.find(List.of(orderId)))
                    .singleElement()
                    .satisfies(summary -> assertThat(summary.carrier()).isEqualTo(ShipmentSummary.Carrier.YAMATO));
        }

        @Test
        @DisplayName("形に合わない送り状番号では出荷を作れず、何も保存されない")
        void rejectsInvalidTrackingNumber() {
            // 操作と確認：業務のエラー TRACKING_NUMBER_INVALID で拒否される
            var orderId = UUID.randomUUID();
            assertBusinessError(() -> shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "ABC", SHIPPED_AT),
                    ShippingErrorCode.TRACKING_NUMBER_INVALID);

            // 確認：この注文の出荷は保存されていない
            assertThat(dsl.fetchCount(SHIPMENT, SHIPMENT.ORDER_ID.eq(orderId))).isZero();
        }
    }

    @Nested
    @DisplayName("出荷の読み出し")
    class Find {

        @Test
        @DisplayName("AC-179 注文 ID でまとめて読むと、出荷のある注文の出荷だけを返す")
        void findsOnlyShippedOrders() {
            // 準備：出荷のある注文と、ない注文
            var shipped = UUID.randomUUID();
            var notShipped = UUID.randomUUID();
            shippingApi.create(shipped, ShipmentSummary.Carrier.JAPAN_POST, "5555", SHIPPED_AT);

            // 操作と確認：出荷のある注文だけ
            assertThat(shippingApi.find(List.of(shipped, notShipped))).extracting(ShipmentSummary::orderId)
                    .containsExactly(shipped);
        }

        @Test
        @DisplayName("AC-179 注文 ID を渡さずに読むと、空を返す")
        void findsNothingForNoOrder() {
            // 操作と確認：空
            assertThat(shippingApi.find(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("返送の注文")
    class Returned {

        @Test
        @DisplayName("AC-189 返送の注文の一覧は、配送状態が返送の出荷の注文を含み、配送中の注文を含まない")
        void listsReturnedOrders() {
            // 準備：返送と配送中の出荷
            var returned = UUID.randomUUID();
            var inTransit = UUID.randomUUID();
            shippingApi.create(returned, ShipmentSummary.Carrier.YAMATO, "7771", SHIPPED_AT);
            shippingApi.create(inTransit, ShipmentSummary.Carrier.YAMATO, "7772", SHIPPED_AT);
            advance(returned, DeliveryStatus.RETURNED);
            advance(inTransit, DeliveryStatus.IN_TRANSIT);

            // 操作と確認：返送だけが入る（他のテストが残した返送も入るため、自分の 2 件だけを見る）
            assertThat(shippingApi.returnedOrderIds()).contains(returned).doesNotContain(inTransit);
        }

        @Test
        @DisplayName("AC-189 配送状態が返送の出荷を読むと、配送状態は返送")
        void findsReturnedStatus() {
            // 準備：返送の出荷
            var returned = UUID.randomUUID();
            shippingApi.create(returned, ShipmentSummary.Carrier.YAMATO, "7773", SHIPPED_AT);
            advance(returned, DeliveryStatus.RETURNED);

            // 操作と確認：配送状態が返送
            assertThat(shippingApi.find(List.of(returned)))
                    .singleElement()
                    .satisfies(summary -> assertThat(summary.status()).isEqualTo(ShipmentSummary.Status.RETURNED));
        }
    }

    @Nested
    @DisplayName("追跡の開始")
    class Tracking {

        @Test
        @DisplayName("AC-174 出荷を作ると、物流サービスで、ハイフンを除いた送り状番号の追跡が始まる")
        void startsTracking() {
            // 操作：ハイフンを含む送り状番号で出荷を作る
            shippingApi.create(UUID.randomUUID(), ShipmentSummary.Carrier.YAMATO, "8881-2223", SHIPPED_AT);

            // 確認：追跡の開始の依頼が処理された後、物流サービスで追跡が始まっている
            asyncEvents.awaitPublishedThenHandled(event -> TrackingRequests.isFor(event, List.of("88812223")));
            assertThat(tracking.started()).contains(new TestCarrierTracking.Started(Carrier.YAMATO, "88812223"));
        }

        @Test
        @DisplayName("AC-174 出荷を作れなかったときは、追跡が始まらない")
        void doesNotStartTrackingWhenRejected() {
            // 準備：出荷を作る
            var orderId = UUID.randomUUID();
            shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "8883-2223", SHIPPED_AT);

            // 操作：同じ注文に、別の送り状番号で出荷を作ろうとする（業務のエラーになる）
            assertBusinessError(() -> shippingApi.create(orderId, ShipmentSummary.Carrier.SAGAWA, "8884-5556", SHIPPED_AT),
                    ShippingErrorCode.SHIPMENT_ALREADY_EXISTS);

            // 確認：最初の出荷の依頼が処理された後も、作れなかった出荷の追跡は始まっていない
            asyncEvents.awaitPublishedThenHandled(event -> TrackingRequests.isFor(event, List.of("88832223", "88845556")));
            assertThat(tracking.started()).doesNotContain(new TestCarrierTracking.Started(Carrier.SAGAWA, "88845556"));
        }

        @Test
        @DisplayName("AC-306 追跡の開始のリスナーは、データベースのトランザクションの外で物流サービスを呼ぶ")
        void startsTrackingOutsideTransaction() {
            // 準備：物流サービスを呼んだ時点の、トランザクションの有無を記録する
            var transactionActive = new CopyOnWriteArrayList<Boolean>();
            doAnswer(invocation -> {
                transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
                return invocation.callRealMethod();
            }).when(tracking).startTracking(any(), eq("88912223"));

            // 操作：出荷を作る
            shippingApi.create(UUID.randomUUID(), ShipmentSummary.Carrier.YAMATO, "8891-2223", SHIPPED_AT);

            // 確認：1 回だけ呼ばれ、そのときトランザクションがない
            asyncEvents.awaitHandled(event -> TrackingRequests.isFor(event, List.of("88912223")));
            assertThat(transactionActive).containsExactly(false);
        }
    }

    @Nested
    @DisplayName("トランザクションの前提")
    class TransactionPrecondition {

        @Test
        @DisplayName("出荷の作成は、トランザクションの外から呼ぶと失敗する")
        void createFailsOutsideTransaction() {
            // 操作と確認：トランザクションの外で作ると失敗する
            var orderId = UUID.randomUUID();
            assertThatThrownBy(() -> shippingApi.create(orderId, ShipmentSummary.Carrier.YAMATO, "777788889999", SHIPPED_AT))
                    .isInstanceOf(IllegalTransactionStateException.class);

            // 確認：出荷は作られない
            assertThat(shippingApi.find(List.of(orderId))).isEmpty();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 出荷の配送状態を進める（通知のエンドポイントを通さない、準備のための操作）。
     *
     * @param orderId 注文 ID
     * @param status  進める先の配送状態
     */
    private void advance(UUID orderId, DeliveryStatus status) {
        var shipment = repository.findAllByOrderIds(List.of(orderId)).getFirst();
        shipment.advanceTo(status, SHIPPED_AT, SHIPPED_AT);
        repository.updateProgress(List.of(shipment));
    }
}
