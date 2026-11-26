package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.id.OperatorId;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationKind;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.event.OrderStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.module.order.domain.order.OrderEventAssertions.assertSingleEventWithStatusChange;
import static com.ecsite.module.order.domain.order.OrderEventAssertions.assertStatusChangedEventOnly;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の支払による状態遷移の単体テスト
 *
 * <p>与信の成功と支払の期限切れによる遷移と、「支払待ち」の注文がキャンセルできないことだけを確認する。
 * 支払の通知を受けてからの流れと在庫を戻すことは {@code OrderPaymentApiTest}、他の状態の注文のキャンセルは
 * {@code OrderCancellationTest} が確認する。
 */
class OrderPaymentTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 与信の成功・期限切れの日時
     */
    private static final Instant LATER = ORDERED_AT.plus(Duration.ofMinutes(10));

    /**
     * 運営者
     */
    private static final Operator OPERATOR = new Operator(new OperatorId(UUID.randomUUID()), "管理 花子");

    /**
     * キャンセル申請中の注文の、申請の日時（注文の 2 分後）
     */
    private static final Instant REQUESTED_AT = ORDERED_AT.plusSeconds(120);

    /**
     * キャンセル申請中の注文の、申請の理由
     */
    private static final String REQUEST_REASON = "理由";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文の成立")
    class Placement {

        @Test
        @DisplayName("AC-143 成立した注文は「支払待ち」で、支払を待っている")
        void isPendingPayment() {
            // 操作：注文を作る
            var order = TestOrders.pending(ORDERED_AT);

            // 確認：支払待ち
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.isAwaitingPayment()).isTrue();
        }
    }

    @Nested
    @DisplayName("与信の成功")
    class AcceptPayment {

        @Test
        @DisplayName("AC-146 支払待ちの注文は、与信の成功で「受付」になり、支払待ちからの状態の変化のイベントが 1 件登録される")
        void acceptsPendingOrder() {
            // 準備：支払待ちの注文
            var order = TestOrders.pending(ORDERED_AT);

            // 操作：与信の成功を受ける
            var changed = order.acceptPayment(LATER);

            // 確認：受付になり、支払を待っていない
            assertThat(changed).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(order.isAwaitingPayment()).isFalse();

            // 確認：支払待ちから受付へのイベントだけで、キャンセルの項目を持たない（イベントは取り出すので最後に確認する）
            assertStatusChangedEventOnly(order, LATER, OrderStatusChanged.Status.PENDING_PAYMENT,
                    OrderStatusChanged.Status.ACCEPTED, null, null);
        }

        @Test
        @DisplayName("BR-17 受付の注文が与信の成功を重ねて受けても、何も変わらない")
        void ignoresWhenAccepted() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT, ORDERED_AT.plusSeconds(60));

            // 操作：与信の成功をもう一度受ける
            var changed = order.acceptPayment(LATER);

            // 確認：受付のまま
            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("期限切れでキャンセルになった注文が与信の成功を受けても、キャンセルのまま")
        void ignoresWhenCancelled() {
            // 準備：期限切れでキャンセルになった注文
            var order = TestOrders.pending(ORDERED_AT);
            order.expirePayment(LATER);

            // 操作：与信の成功を受ける
            var changed = order.acceptPayment(LATER);

            // 確認：キャンセルのまま
            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("BR-17 キャンセル申請中の注文が与信の成功を受けても、状態とイベントを変えない")
        void ignoresWhenCancellationRequested() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT, ORDERED_AT.plusSeconds(60), REQUEST_REASON, REQUESTED_AT);

            // 操作：与信の成功を受ける
            var changed = order.acceptPayment(LATER);

            // 確認：申請中のままで、イベントもない
            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);
            assertThat(order.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("BR-17 出荷済みの注文が与信の成功を受けても、状態とイベントを変えない")
        void ignoresWhenShipped() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT, ORDERED_AT.plusSeconds(60), "123456789012", ORDERED_AT.plusSeconds(120));

            // 操作：与信の成功を受ける
            var changed = order.acceptPayment(LATER);

            // 確認：出荷済みのままで、イベントもない
            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(order.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("支払の期限切れ")
    class ExpirePayment {

        @Test
        @DisplayName("受付の注文は、支払の期限切れを受けても状態とイベントを変えない")
        void ignoresWhenAccepted() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT, ORDERED_AT.plusSeconds(60));

            // 操作：支払の期限切れを受ける
            var changed = order.expirePayment(LATER);

            // 確認：受付のままで、イベントもない
            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(order.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("NFR-7 支払の期限切れを重ねて受けても、イベントは 1 回だけ")
        void expiresOnlyOnce() {
            // 準備：期限切れでキャンセルになり、イベントを取り出した注文
            var order = TestOrders.pending(ORDERED_AT);
            order.expirePayment(LATER);
            order.pullEvents();

            // 操作：支払の期限切れをもう一度受ける
            var changed = order.expirePayment(LATER);

            // 確認：何もせず、イベントもない
            assertThat(changed).isFalse();
            assertThat(order.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-153、155 支払待ちの注文は、支払の期限切れでキャンセルになり、種類が支払期限切れの記録と、在庫に戻すイベントが 1 件だけ登録される")
        void registersPaymentExpiredEvent() {
            // 準備：支払待ちの注文
            var order = TestOrders.pending(ORDERED_AT);

            // 操作：支払の期限切れを受ける
            var changed = order.expirePayment(LATER);

            // 確認：キャンセルになった
            assertThat(changed).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);

            // 確認：支払期限切れのキャンセルの記録
            assertPaymentExpired(order.cancellation());

            // 確認：イベントは取り出すので最後に確認する。全体が 1 件で、支払待ちからキャンセルへの状態の変化
            assertSingleEventWithStatusChange(order, LATER, OrderStatusChanged.Status.PENDING_PAYMENT,
                    OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.PAYMENT_EXPIRED, true);
        }

        @Test
        @DisplayName("キャンセル申請中の注文は、支払の期限切れを受けても状態・申請・イベントを変えない")
        void ignoresWhenCancellationRequested() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT, ORDERED_AT.plusSeconds(60), REQUEST_REASON, REQUESTED_AT);

            // 操作：支払の期限切れを受ける
            var changed = order.expirePayment(LATER);

            // 確認：何もせず、申請中のままで、キャンセルの記録もイベントもない
            assertThat(changed).isFalse();
            assertUnchangedRequested(order);
        }

        @Test
        @DisplayName("出荷済みの注文は、支払の期限切れを受けてもキャンセルにならない")
        void ignoresWhenShipped() {
            // 準備：出荷済みの注文
            var order = TestOrders.shipped(ORDERED_AT, ORDERED_AT.plusSeconds(60), "123456789012", ORDERED_AT.plusSeconds(120));

            // 操作：支払の期限切れを受ける
            var changed = order.expirePayment(LATER);

            // 確認：出荷済みのままで、キャンセルの記録もイベントもない
            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(order.cancellation()).isNull();
            assertThat(order.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("支払待ちの注文のキャンセル")
    class CancelPending {

        @Test
        @DisplayName("AC-158 支払待ちの注文は、消費者がキャンセルを申請できる操作に出さない")
        void isNotRequestable() {
            // 操作と確認：申請できない
            assertThat(TestOrders.pending(ORDERED_AT).isCancellationRequestable()).isFalse();
        }

        @Test
        @DisplayName("AC-158 支払待ちの注文へのキャンセルの申請は拒否される")
        void rejectsRequest() {
            // 準備：支払待ちの注文
            var order = TestOrders.pending(ORDERED_AT);

            // 操作と確認：業務のエラー CANCELLATION_NOT_REQUESTABLE で拒否される
            assertBusinessError(() -> order.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }

        @Test
        @DisplayName("AC-158 支払待ちの注文は、運営者がキャンセルできる操作に出さない")
        void isNotCancellableByOperator() {
            // 操作と確認：運営者はキャンセルできない
            assertThat(TestOrders.pending(ORDERED_AT).isCancellableByOperator(false)).isFalse();
        }

        @Test
        @DisplayName("AC-158 支払待ちの注文への運営者のキャンセルは拒否され、支払待ちのまま")
        void rejectsOperatorCancellation() {
            // 準備：支払待ちの注文
            var order = TestOrders.pending(ORDERED_AT);

            // 操作と確認：業務のエラー AWAITING_PAYMENT で拒否される
            assertBusinessError(() -> order.cancelByOperator("理由", true, false, OPERATOR, LATER), OrderErrorCode.AWAITING_PAYMENT);

            // 確認：支払待ちのまま
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("支払待ちの注文は、申請がないので承認できず、支払待ちのまま")
        void rejectsApproval() {
            // 準備：支払待ちの注文
            var order = TestOrders.pending(ORDERED_AT);

            // 操作と確認：業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> order.approveCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);

            // 確認：支払待ちのままで、申請もキャンセルの記録もない
            assertUnchangedPending(order);
        }

        @Test
        @DisplayName("支払待ちの注文は、申請がないので却下できず、支払待ちのまま")
        void rejectsRejection() {
            // 準備：支払待ちの注文
            var order = TestOrders.pending(ORDERED_AT);

            // 操作と確認：業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> order.rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);

            // 確認：支払待ちのままで、申請もキャンセルの記録もない
            assertUnchangedPending(order);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * キャンセルの記録が、{@link #LATER} の支払期限切れのものであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>種類が支払期限切れ</li>
     *   <li>キャンセルの日時が期限切れを受けた日時</li>
     *   <li>理由の文がない</li>
     *   <li>運営者がない</li>
     * </ul>
     *
     * @param cancellation 確認するキャンセルの記録
     */
    private static void assertPaymentExpired(Cancellation cancellation) {
        assertThat(cancellation.kind()).isEqualTo(CancellationKind.PAYMENT_EXPIRED);
        assertThat(cancellation.cancelledAt()).isEqualTo(LATER);
        assertThat(cancellation.reason()).isNull();
        assertThat(cancellation.cancelledBy()).isNull();
    }

    /**
     * 操作の後も、注文が {@link TestOrders#requested} の申請中のままであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態がキャンセル申請中</li>
     *   <li>申請が、準備の日時と理由で、判断を待つ</li>
     *   <li>キャンセルの記録がない</li>
     *   <li>イベントがない</li>
     * </ul>
     *
     * @param order 確認する注文
     */
    private static void assertUnchangedRequested(Order order) {
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);
        assertThat(order.cancellationRequest())
                .isEqualTo(new CancellationRequest(REQUESTED_AT, REQUEST_REASON, CancellationRequest.Result.PENDING, null, null));
        assertThat(order.cancellation()).isNull();
        assertThat(order.pullEvents()).isEmpty();
    }

    /**
     * 拒否された操作の後も、注文が支払待ちのままであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が支払待ち</li>
     *   <li>キャンセルの申請がない</li>
     *   <li>キャンセルの記録がない</li>
     *   <li>イベントがない</li>
     * </ul>
     *
     * @param order 確認する注文
     */
    private static void assertUnchangedPending(Order order) {
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.cancellationRequest()).isNull();
        assertThat(order.cancellation()).isNull();
        assertThat(order.pullEvents()).isEmpty();
    }

}
