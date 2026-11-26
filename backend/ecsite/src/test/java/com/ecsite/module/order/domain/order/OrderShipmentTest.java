package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.id.OperatorId;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationKind;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.order.event.OrderStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.module.order.domain.order.OrderEventAssertions.assertStatusChangedEventOnly;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の出荷と、出荷済みの注文のキャンセルの単体テスト
 *
 * <p>出荷による遷移と、出荷済みの注文の運営者のキャンセルと、画面に返す操作のフラグだけを確認する。売上の確定と
 * API を通した流れは {@code ShipmentApiTest}、在庫に戻すことは {@code ReturnedOrderCancellationApiTest} が確認する。
 */
class OrderShipmentTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 出荷・キャンセルの日時
     */
    private static final Instant LATER = ORDERED_AT.plus(Duration.ofDays(1));

    /**
     * キャンセルした運営者
     */
    private static final Operator OPERATOR = new Operator(new OperatorId(UUID.randomUUID()), "管理 花子");

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "123456789012";

    /**
     * {@link TestOrders#pending} が作る注文の、注文番号
     */
    private static final String ORDER_NUMBER = "20261008-000001";

    /**
     * 返送された注文のキャンセルの理由
     */
    private static final String RETURNED_REASON = "返送された";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("出荷")
    class Ship {

        @Test
        @DisplayName("AC-168 出荷すると、出荷通知に使う注文の出荷のイベントが登録される")
        void registersShippedEvent() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：出荷する
            order.ship(Carrier.YAMATO, TRACKING_NUMBER, LATER);

            // 確認：この注文の出荷の内容を持つイベントが 1 件
            assertShippedEvent(order);
        }

        @Test
        @DisplayName("AC-168 出荷すると注文は「出荷済み」になり、「受付 → 出荷済み」の状態の変化のイベントが登録される")
        void registersStatusChangedEvent() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：出荷する
            order.ship(Carrier.YAMATO, TRACKING_NUMBER, LATER);

            // 確認：出荷済み
            assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);

            // 確認：受付から出荷済みへのイベントだけで、キャンセルの項目を持たない
            assertStatusChangedEventOnly(order, LATER, OrderStatusChanged.Status.ACCEPTED,
                    OrderStatusChanged.Status.SHIPPED, null, null);
        }
    }

    @Nested
    @DisplayName("出荷済みの注文の申請と判断")
    class RequestShipped {

        @Test
        @DisplayName("AC-171 出荷済みの注文は、消費者がキャンセルを申請できず、状態と申請を変えない")
        void rejectsRequest() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT);

            // 操作と確認：業務のエラー CANCELLATION_NOT_REQUESTABLE で拒否される
            assertBusinessError(() -> order.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);

            // 確認：出荷済みのままで、申請もイベントもない
            assertUnchangedShipped(order);
        }

        @Test
        @DisplayName("出荷済みの注文は、申請がないので承認できず、状態を変えない")
        void rejectsApproval() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT);

            // 操作と確認：業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> order.approveCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);

            // 確認：出荷済みのままで、申請もイベントもない
            assertUnchangedShipped(order);
        }

        @Test
        @DisplayName("出荷済みの注文は、申請がないので却下できず、状態を変えない")
        void rejectsRejection() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT);

            // 操作と確認：業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> order.rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);

            // 確認：出荷済みのままで、申請もイベントもない
            assertUnchangedShipped(order);
        }
    }

    @Nested
    @DisplayName("「受付」でない注文の出荷")
    class ShipRejected {

        @Test
        @DisplayName("AC-170 「支払待ち」の注文は出荷できない")
        void rejectsPending() {
            // 操作と確認：業務のエラー ORDER_NOT_SHIPPABLE で拒否される
            assertNotShippable(pendingOrder());
        }

        @Test
        @DisplayName("AC-170 「キャンセル申請中」の注文は出荷できない")
        void rejectsRequested() {
            // 操作と確認：業務のエラー ORDER_NOT_SHIPPABLE で拒否される
            assertNotShippable(TestOrders.requested(ORDERED_AT, ORDERED_AT.plusSeconds(30), null, LATER));
        }

        @Test
        @DisplayName("AC-170 キャンセルになった注文は出荷できない")
        void rejectsCancelled() {
            // 準備：運営者がキャンセルした注文
            var cancelled = TestOrders.accepted(ORDERED_AT);
            cancelled.cancelByOperator("在庫の不備", true, false, OPERATOR, LATER);

            // 操作と確認：業務のエラー ORDER_NOT_SHIPPABLE で拒否される
            assertNotShippable(cancelled);
        }

        @Test
        @DisplayName("AC-170 「出荷済み」の注文は、もう一度出荷できない")
        void rejectsShipped() {
            // 操作と確認：業務のエラー ORDER_NOT_SHIPPABLE で拒否される
            assertNotShippable(TestOrders.shipped(ORDERED_AT));
        }
    }

    @Nested
    @DisplayName("出荷済みの注文のキャンセル")
    class CancelShipped {

        @Test
        @DisplayName("AC-190 返送された出荷済みの注文は、運営者がキャンセルできる")
        void cancelsReturnedOrder() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT);

            // 操作：返送されたので、在庫に戻してキャンセルする
            var cancelled = order.cancelByOperator(RETURNED_REASON, true, true, OPERATOR, LATER);

            // 確認：キャンセル
            assertThat(cancelled).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("AC-190、191 返送された注文のキャンセルは、理由と運営者と、在庫に戻すことを記録する")
        void recordsRestocked() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT);

            // 操作：返送されたので、在庫に戻してキャンセルする
            order.cancelByOperator(RETURNED_REASON, true, true, OPERATOR, LATER);

            // 確認：運営者のキャンセルで、在庫に戻す
            assertThat(order.cancellation())
                    .isEqualTo(new Cancellation(LATER, CancellationKind.OPERATOR, RETURNED_REASON, OPERATOR, true));
        }

        @Test
        @DisplayName("AC-190 返送された注文のキャンセルで、注文キャンセルのイベントが登録される")
        void registersCancelledEvent() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT);

            // 操作：返送されたので、在庫に戻してキャンセルする
            order.cancelByOperator(RETURNED_REASON, true, true, OPERATOR, LATER);

            // 確認：キャンセルへの状態の変化のイベントだけ
            assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderStatusChanged.class,
                    event -> assertThat(event.to()).isEqualTo(OrderStatusChanged.Status.CANCELLED));
        }

        @Test
        @DisplayName("AC-191 返送された注文を在庫に戻さずにキャンセルすると、キャンセルして在庫に戻さないことを記録し、前の状態が出荷済みで在庫に戻さないイベントが登録される")
        void registersNotRestockedEvent() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT);

            // 操作：返送されたので、在庫に戻さずにキャンセルする
            var cancelled = order.cancelByOperator(RETURNED_REASON, false, true, OPERATOR, LATER);

            // 確認：キャンセルし、在庫に戻さないことを記録する
            assertThat(cancelled).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellation().restocked()).isFalse();

            // 確認：前の状態が出荷済みで、キャンセルの種類と在庫に戻さないことを持つイベントが 1 件
            assertStatusChangedEventOnly(order, LATER, OrderStatusChanged.Status.SHIPPED,
                    OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.OPERATOR, false);
        }

        @Test
        @DisplayName("AC-171 返送でない出荷済みの注文は、運営者もキャンセルできない")
        void rejectsNotReturned() {
            // 操作と確認：業務のエラー SHIPPED_ORDER_NOT_RETURNED で拒否される
            assertBusinessError(() -> TestOrders.shipped(ORDERED_AT).cancelByOperator("理由", true, false, OPERATOR, LATER),
                    OrderErrorCode.SHIPPED_ORDER_NOT_RETURNED);
        }

        @Test
        @DisplayName("AC-191 出荷済みの注文のキャンセルでは、在庫に戻すかの指定が必須")
        void requiresRestockChoice() {
            // 操作と確認：業務のエラー RESTOCK_CHOICE_REQUIRED で拒否される
            assertBusinessError(() -> TestOrders.shipped(ORDERED_AT).cancelByOperator("理由", null, true, OPERATOR, LATER),
                    OrderErrorCode.RESTOCK_CHOICE_REQUIRED);
        }

        @Test
        @DisplayName("AC-191 出荷済みでない注文のキャンセルは、指定によらず在庫に戻す")
        void alwaysRestocksNotShipped() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：在庫に戻さない指定でキャンセルする
            order.cancelByOperator("在庫の不備", false, false, OPERATOR, LATER);

            // 確認：在庫に戻す
            assertThat(order.cancellation().restocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("出荷できるかのフラグ")
    class ShippableFlag {

        @Test
        @DisplayName("AC-167 「受付」の注文は、出荷を登録できる")
        void acceptedIsShippable() {
            // 操作と確認：出荷できる
            assertThat(TestOrders.accepted(ORDERED_AT).isShippable()).isTrue();
        }

        @Test
        @DisplayName("AC-170 「支払待ち」の注文は、出荷を登録できる操作に出さない")
        void pendingIsNotShippable() {
            // 操作と確認：出荷できない
            assertThat(pendingOrder().isShippable()).isFalse();
        }

        @Test
        @DisplayName("AC-170 「キャンセル申請中」の注文は、出荷を登録できる操作に出さない")
        void requestedIsNotShippable() {
            // 操作と確認：出荷できない
            assertThat(TestOrders.requested(ORDERED_AT, ORDERED_AT.plusSeconds(30), null, LATER).isShippable()).isFalse();
        }

        @Test
        @DisplayName("AC-170 「出荷済み」の注文は、出荷を登録できる操作に出さない")
        void shippedIsNotShippable() {
            // 操作と確認：出荷できない
            assertThat(TestOrders.shipped(ORDERED_AT).isShippable()).isFalse();
        }
    }

    @Nested
    @DisplayName("運営者がキャンセルできるかのフラグ")
    class OperatorCancellableFlag {

        @Test
        @DisplayName("AC-171、190 返送された出荷済みの注文は、運営者がキャンセルできる")
        void returnedIsCancellable() {
            // 操作と確認：配送状態が返送ならキャンセルできる
            assertThat(TestOrders.shipped(ORDERED_AT).isCancellableByOperator(true)).isTrue();
        }

        @Test
        @DisplayName("AC-171 返送でない出荷済みの注文は、運営者がキャンセルできる操作に出さない")
        void notReturnedIsNotCancellable() {
            // 操作と確認：配送状態が返送でなければキャンセルできない
            assertThat(TestOrders.shipped(ORDERED_AT).isCancellableByOperator(false)).isFalse();
        }

        @Test
        @DisplayName("AC-103 出荷していない「受付」の注文は、返送でなくても運営者がキャンセルできる")
        void acceptedIsCancellable() {
            // 操作と確認：キャンセルできる
            assertThat(TestOrders.accepted(ORDERED_AT).isCancellableByOperator(false)).isTrue();
        }

        @Test
        @DisplayName("AC-158 「支払待ち」の注文は、返送の指定があっても運営者がキャンセルできる操作に出さない")
        void pendingIsNotCancellable() {
            // 操作と確認：キャンセルできない
            assertThat(pendingOrder().isCancellableByOperator(true)).isFalse();
        }
    }

    @Nested
    @DisplayName("出荷済みかのフラグ")
    class ShippedFlag {

        @Test
        @DisplayName("AC-171 「出荷済み」の注文は、出荷済みと判定する")
        void shippedIsShipped() {
            // 操作と確認：出荷済み
            assertThat(TestOrders.shipped(ORDERED_AT).isShipped()).isTrue();
        }

        @Test
        @DisplayName("AC-171 出荷していない「受付」の注文は、出荷済みと判定しない")
        void acceptedIsNotShipped() {
            // 操作と確認：出荷済みでない
            assertThat(TestOrders.accepted(ORDERED_AT).isShipped()).isFalse();
        }

        @Test
        @DisplayName("AC-171 返送でキャンセルした注文は、出荷済みと判定しない")
        void cancelledIsNotShipped() {
            // 準備：返送されてキャンセルした注文
            var order = TestOrders.shipped(ORDERED_AT);
            order.cancelByOperator(RETURNED_REASON, true, true, OPERATOR, LATER);

            // 操作と確認：出荷済みでない
            assertThat(order.isShipped()).isFalse();
        }
    }

    @Nested
    @DisplayName("在庫に戻すかを聞くかのフラグ")
    class AsksRestockFlag {

        @Test
        @DisplayName("AC-191 返送された出荷済みの注文のキャンセルでは、在庫に戻すかを聞く")
        void asksForReturnedShipped() {
            // 操作と確認：聞く
            assertThat(TestOrders.shipped(ORDERED_AT).asksRestockOnCancel(true)).isTrue();
        }

        @Test
        @DisplayName("AC-191 返送でない出荷済みの注文では、在庫に戻すかを聞かない")
        void doesNotAskForNotReturned() {
            // 操作と確認：聞かない
            assertThat(TestOrders.shipped(ORDERED_AT).asksRestockOnCancel(false)).isFalse();
        }

        @Test
        @DisplayName("AC-191 出荷していない「受付」の注文は、返送の指定があっても在庫に戻すかを聞かない")
        void doesNotAskForAccepted() {
            // 操作と確認：出荷前は必ず戻すので聞かない
            assertThat(TestOrders.accepted(ORDERED_AT).asksRestockOnCancel(true)).isFalse();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の出荷のイベントが、この注文と出荷の内容で 1 件だけ登録されていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>注文の出荷のイベントが 1 件だけ</li>
     *   <li>注文 ID がこの注文</li>
     *   <li>出荷の日時</li>
     *   <li>注文番号</li>
     *   <li>配送業者</li>
     *   <li>送り状番号</li>
     * </ul>
     *
     * @param order 確認する注文
     */
    private static void assertShippedEvent(Order order) {
        assertThat(order.pullEvents()).filteredOn(OrderShipped.class::isInstance).singleElement()
                .isInstanceOfSatisfying(OrderShipped.class, event -> {
                    assertThat(event.orderId()).isEqualTo(order.id());
                    assertThat(event.occurredAt()).isEqualTo(LATER);
                    assertThat(event.orderNumber()).isEqualTo(ORDER_NUMBER);
                    assertThat(event.carrier()).isEqualTo(OrderShipped.Carrier.YAMATO);
                    assertThat(event.trackingNumber()).isEqualTo(TRACKING_NUMBER);
                });
    }

    /**
     * 注文が出荷できないことを確認する。
     *
     * @param order 確認する注文
     */
    private static void assertNotShippable(Order order) {
        assertBusinessError(() -> order.ship(Carrier.YAMATO, TRACKING_NUMBER, LATER), OrderErrorCode.ORDER_NOT_SHIPPABLE);
    }

    /**
     * 拒否された操作の後も、注文が出荷済みのままであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が出荷済み</li>
     *   <li>キャンセルの申請がない</li>
     *   <li>キャンセルの記録がない</li>
     *   <li>イベントがない</li>
     * </ul>
     *
     * @param order 確認する注文
     */
    private static void assertUnchangedShipped(Order order) {
        assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.cancellationRequest()).isNull();
        assertThat(order.cancellation()).isNull();
        assertThat(order.pullEvents()).isEmpty();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 「支払待ち」の注文を作る。
     *
     * <p>成立のイベントは取り出してある。
     *
     * @return 支払待ちの注文
     */
    private static Order pendingOrder() {
        return TestOrders.pending(ORDERED_AT);
    }
}
