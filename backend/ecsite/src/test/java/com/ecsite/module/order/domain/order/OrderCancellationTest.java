package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.id.OperatorId;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationKind;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.domain.order.vo.CancellationRequest.Result;
import com.ecsite.module.order.event.OrderStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.module.order.domain.order.OrderEventAssertions.assertSingleEventWithStatusChange;
import static com.ecsite.module.order.domain.order.OrderEventAssertions.assertStatusChangedEventOnly;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文のキャンセルの状態遷移の単体テスト
 *
 * <p>「受付」と「キャンセル申請中」の注文の、申請・承認・却下・運営者のキャンセルと、画面に返す操作のフラグだけを確認する。
 * 支払待ちの注文は {@code OrderPaymentTest}、出荷済みの注文は {@code OrderShipmentTest}、在庫を戻すことと API を通した
 * 流れは {@code CancellationApiTest} が確認する。
 */
class OrderCancellationTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 申請・判断の日時
     */
    private static final Instant LATER = ORDERED_AT.plus(Duration.ofHours(1));

    /**
     * 判断・キャンセルした運営者
     */
    private static final Operator OPERATOR = new Operator(new OperatorId(UUID.randomUUID()), "管理 花子");

    /**
     * 消費者の申請の理由
     */
    private static final String REQUEST_REASON = "サイズを間違えました";

    /**
     * 運営者のキャンセルの理由
     */
    private static final String OPERATOR_REASON = "在庫の不備";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("キャンセルの申請")
    class Request {

        @Test
        @DisplayName("AC-95、「受付」の注文を申請すると「キャンセル申請中」になり、日時と理由を記録して判断を待ち、「受付 → キャンセル申請中」の状態の変化のイベントが登録される")
        void registersRequestedEvent() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：理由を添えて申請する
            var changed = order.requestCancellation(REQUEST_REASON, LATER);

            // 確認：状態を変えたので true。キャンセル申請中
            assertThat(changed).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);

            // 確認：判断を待つ申請（日時と添えた理由を記録）
            assertThat(order.cancellationRequest())
                    .isEqualTo(new CancellationRequest(LATER, REQUEST_REASON, Result.PENDING, null, null));

            // 確認：登録されたイベントは受付からキャンセル申請中への 1 件だけで、キャンセルの項目を持たない（イベントを見るので最後）
            assertSingleEventWithStatusChange(order, LATER, OrderStatusChanged.Status.ACCEPTED,
                    OrderStatusChanged.Status.CANCELLATION_REQUESTED, null, null);
        }

        @Test
        @DisplayName("AC-95 理由を添えずに申請できる")
        void requestsWithoutReason() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：理由なしで申請する
            order.requestCancellation(null, LATER);

            // 確認：理由のない申請で、キャンセル申請中
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);
            assertThat(order.cancellationRequest().reason()).isNull();
        }

        @Test
        @DisplayName("AC-95 空白だけの理由は、理由なしとして扱う")
        void treatsBlankReasonAsNone() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：空白だけの理由で申請する
            order.requestCancellation(" ", LATER);

            // 確認：理由がない
            assertThat(order.cancellationRequest().reason()).isNull();
        }

        @Test
        @DisplayName("AC-95 500 文字の理由は受け付ける")
        void acceptsLongestReason() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：500 文字の理由で申請する
            order.requestCancellation("あ".repeat(Order.REASON_MAX_LENGTH), LATER);

            // 確認：切り詰めずに記録する
            assertThat(order.cancellationRequest().reason()).hasSize(Order.REASON_MAX_LENGTH);
        }

        @Test
        @DisplayName("AC-95 501 文字の理由は拒否される")
        void rejectsTooLongReason() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作と確認：業務のエラー INVALID_CANCELLATION_REASON で拒否される
            assertBusinessError(() -> order.requestCancellation("あ".repeat(Order.REASON_MAX_LENGTH + 1), LATER), OrderErrorCode.INVALID_CANCELLATION_REASON);
        }

        @Test
        @DisplayName("AC-96 申請中の注文にもう一度申請しても、何も変わらない")
        void ignoresRepeatedRequest() {
            // 準備：最初の理由で申請した注文
            var order = TestOrders.accepted(ORDERED_AT);
            order.requestCancellation("最初の理由", LATER);

            // 操作：別の理由で、後からもう一度申請する
            var changed = order.requestCancellation("次の理由", LATER.plusSeconds(60));

            // 確認：状態を変えなかったので false。最初の申請のまま
            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);
            assertThat(order.cancellationRequest().reason()).isEqualTo("最初の理由");
            assertThat(order.cancellationRequest().requestedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("AC-96 却下された注文は、もう一度申請できない")
        void rejectsRequestAfterRejection() {
            // 準備：申請が却下された注文
            var rejected = rejectedOrder();

            // 操作と確認：業務のエラー CANCELLATION_NOT_REQUESTABLE で拒否される
            assertBusinessError(() -> rejected.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }

        @Test
        @DisplayName("AC-95 運営者がキャンセルした注文は、申請できない")
        void rejectsRequestForCancelledOrder() {
            // 準備：運営者がキャンセルした注文
            var cancelled = cancelledByOperatorOrder();

            // 操作と確認：業務のエラー CANCELLATION_NOT_REQUESTABLE で拒否される
            assertBusinessError(() -> cancelled.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }
    }

    @Nested
    @DisplayName("申請の承認")
    class Approve {

        @Test
        @DisplayName("AC-101 申請を承認すると、キャンセルになる")
        void becomesCancelled() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：承認する
            var cancelled = order.approveCancellation(OPERATOR, LATER);

            // 確認：キャンセル
            assertThat(cancelled).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("AC-101 承認すると、申請に承認の結果と判断の日時を記録する")
        void recordsApproval() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：承認する
            order.approveCancellation(OPERATOR, LATER);

            // 確認：承認の結果と日時
            assertThat(order.cancellationRequest().result()).isEqualTo(Result.APPROVED);
            assertThat(order.cancellationRequest().decidedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("AC-101 承認によるキャンセルは、種類が申請の承認で、承認した運営者を記録する")
        void recordsApprovedCancellation() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：承認する
            order.approveCancellation(OPERATOR, LATER);

            // 確認：申請の承認のキャンセルの記録
            assertThat(order.cancellation()).isEqualTo(new Cancellation(LATER, CancellationKind.REQUEST_APPROVED, null, OPERATOR));
        }

        @Test
        @DisplayName("AC-101 承認すると、在庫を戻すための注文キャンセルのイベントが登録される")
        void registersCancelledEvent() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：承認する
            order.approveCancellation(OPERATOR, LATER);

            // 確認：キャンセルへの状態の変化のイベントだけ
            OrderEventAssertions.assertCancelledEventOnly(order);
        }

        @Test
        @DisplayName("AC-101 承認のイベントは、前の状態がキャンセル申請中で、種類が申請の承認で、在庫に戻す")
        void registersApprovedEvent() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：承認する
            order.approveCancellation(OPERATOR, LATER);

            // 確認：イベントの前の状態・種類・在庫に戻すか
            assertStatusChangedEventOnly(order, LATER, OrderStatusChanged.Status.CANCELLATION_REQUESTED,
                    OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.REQUEST_APPROVED, true);
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文の承認は、成功し、キャンセルの記録を変えない")
        void ignoresApprovalOfCancelledOrder() {
            // 準備：承認でキャンセルになった注文
            var order = approvedOrder();

            // 操作：後からもう一度承認する
            var cancelled = order.approveCancellation(OPERATOR, LATER.plusSeconds(60));

            // 確認：何もせず、最初のキャンセルのまま
            assertThat(cancelled).isFalse();
            assertThat(order.cancellation().cancelledAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文の承認では、在庫を戻すイベントを登録しない")
        void registersNoEventForCancelledOrder() {
            // 準備：承認でキャンセルになった注文
            var order = approvedOrder();

            // 操作：後からもう一度承認する
            order.approveCancellation(OPERATOR, LATER.plusSeconds(60));

            // 確認：イベントがない
            assertThat(order.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("申請のない「受付」の注文は、承認できない")
        void rejectsApprovalWithoutRequest() {
            // 操作と確認：業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> TestOrders.accepted(ORDERED_AT).approveCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("申請の却下")
    class Reject {

        @Test
        @DisplayName("AC-102 申請を却下すると「受付」に戻り、キャンセルの記録はない")
        void returnsToAccepted() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：却下する
            order.rejectCancellation(OPERATOR, LATER);

            // 確認：受付で、キャンセルしていない
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(order.cancellation()).isNull();
        }

        @Test
        @DisplayName("AC-102 却下すると、申請に却下の結果と、判断の日時と運営者を記録する")
        void recordsRejection() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：却下する
            order.rejectCancellation(OPERATOR, LATER);

            // 確認：却下の結果、日時、運営者
            assertThat(order.cancellationRequest().result()).isEqualTo(Result.REJECTED);
            assertThat(order.cancellationRequest().decidedAt()).isEqualTo(LATER);
            assertThat(order.cancellationRequest().decidedBy()).isEqualTo(OPERATOR);
        }

        @Test
        @DisplayName("AC-102 却下すると、「キャンセル申請中 → 受付」の状態の変化のイベントが登録される")
        void registersRejectedEvent() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：却下する
            order.rejectCancellation(OPERATOR, LATER);

            // 確認：キャンセル申請中から受付へのイベントだけで、キャンセルの項目を持たない
            assertStatusChangedEventOnly(order, LATER, OrderStatusChanged.Status.CANCELLATION_REQUESTED,
                    OrderStatusChanged.Status.ACCEPTED, null, null);
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文の申請は、却下できない")
        void rejectsRejectionOfCancelledOrder() {
            // 準備：承認でキャンセルになった注文
            var order = approvedOrder();

            // 操作と確認：業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> order.rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
        }

        @Test
        @DisplayName("申請のない「受付」の注文は、却下できない")
        void rejectsRejectionWithoutRequest() {
            // 操作と確認：業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> TestOrders.accepted(ORDERED_AT).rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("運営者の判断によるキャンセル")
    class CancelByOperator {

        @Test
        @DisplayName("AC-103 運営者は「受付」の注文をキャンセルできる")
        void cancelsAcceptedOrder() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：理由を記録してキャンセルする
            var cancelled = order.cancelByOperator(OPERATOR_REASON, true, false, OPERATOR, LATER);

            // 確認：キャンセル
            assertThat(cancelled).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("AC-103 運営者のキャンセルは、種類が運営者で、理由と運営者を記録する")
        void recordsOperatorCancellation() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：理由を記録してキャンセルする
            order.cancelByOperator(OPERATOR_REASON, true, false, OPERATOR, LATER);

            // 確認：運営者のキャンセルの記録
            assertThat(order.cancellation()).isEqualTo(new Cancellation(LATER, CancellationKind.OPERATOR, OPERATOR_REASON, OPERATOR));
        }

        @Test
        @DisplayName("AC-103 運営者のキャンセルで、在庫を戻すための注文キャンセルのイベントが登録される")
        void registersCancelledEvent() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：理由を記録してキャンセルする
            order.cancelByOperator(OPERATOR_REASON, true, false, OPERATOR, LATER);

            // 確認：キャンセルへの状態の変化のイベントだけ
            OrderEventAssertions.assertCancelledEventOnly(order);
        }

        @Test
        @DisplayName("AC-103 運営者のキャンセルのイベントは、前の状態が受付で、種類が運営者で、在庫に戻す")
        void registersOperatorEvent() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：在庫に戻さない指定でキャンセルする（出荷前は指定によらず戻す）
            order.cancelByOperator(OPERATOR_REASON, false, false, OPERATOR, LATER);

            // 確認：イベントの前の状態・種類・在庫に戻すか
            assertStatusChangedEventOnly(order, LATER, OrderStatusChanged.Status.ACCEPTED,
                    OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.OPERATOR, true);
        }

        @Test
        @DisplayName("AC-103 「キャンセル申請中」の注文を運営者がキャンセルしたイベントは、前の状態がキャンセル申請中で、種類が運営者")
        void registersOperatorEventFromRequested() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：理由を記録してキャンセルする
            order.cancelByOperator(OPERATOR_REASON, true, false, OPERATOR, LATER);

            // 確認：イベントの前の状態・種類・在庫に戻すか
            assertStatusChangedEventOnly(order, LATER, OrderStatusChanged.Status.CANCELLATION_REQUESTED,
                    OrderStatusChanged.Status.CANCELLED, OrderStatusChanged.CancellationKind.OPERATOR, true);
        }

        @Test
        @DisplayName("AC-103 運営者は「キャンセル申請中」の注文もキャンセルできる")
        void cancelsRequestedOrder() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：理由を記録してキャンセルする
            order.cancelByOperator("お客様の申請による", true, false, OPERATOR, LATER);

            // 確認：運営者のキャンセル
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellation().kind()).isEqualTo(CancellationKind.OPERATOR);
        }

        @Test
        @DisplayName("AC-103 「キャンセル申請中」の注文を運営者がキャンセルすると、申請は承認されたものとする")
        void approvesPendingRequest() {
            // 準備：キャンセル申請中の注文
            var order = TestOrders.requested(ORDERED_AT);

            // 操作：理由を記録してキャンセルする
            order.cancelByOperator("お客様の申請による", true, false, OPERATOR, LATER);

            // 確認：申請が承認
            assertThat(order.cancellationRequest().result()).isEqualTo(Result.APPROVED);
        }

        @ParameterizedTest(name = "「{0}」")
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("AC-103 理由のない運営者のキャンセルは拒否される")
        void requiresReason(String reason) {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作と確認：業務のエラー CANCELLATION_REASON_REQUIRED で拒否される
            assertBusinessError(() -> order.cancelByOperator(reason, true, false, OPERATOR, LATER), OrderErrorCode.CANCELLATION_REASON_REQUIRED);
        }

        @Test
        @DisplayName("AC-103 運営者のキャンセルの 500 文字の理由は受け付ける")
        void acceptsLongestReason() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作：500 文字の理由でキャンセルする
            order.cancelByOperator("あ".repeat(Order.REASON_MAX_LENGTH), true, false, OPERATOR, LATER);

            // 確認：切り詰めずに記録する
            assertThat(order.cancellation().reason()).hasSize(Order.REASON_MAX_LENGTH);
        }

        @Test
        @DisplayName("AC-103 運営者のキャンセルの 501 文字の理由は拒否される")
        void rejectsTooLongReason() {
            // 準備：受付の注文
            var order = TestOrders.accepted(ORDERED_AT);

            // 操作と確認：業務のエラー INVALID_CANCELLATION_REASON で拒否される
            assertBusinessError(() -> order.cancelByOperator("あ".repeat(Order.REASON_MAX_LENGTH + 1), true, false, OPERATOR, LATER),
                    OrderErrorCode.INVALID_CANCELLATION_REASON);
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文への運営者のキャンセルは、成功し、理由を変えない")
        void ignoresCancelledOrder() {
            // 準備：最初の理由で運営者がキャンセルした注文
            var order = TestOrders.accepted(ORDERED_AT);
            order.cancelByOperator("最初の理由", true, false, OPERATOR, LATER);

            // 操作：別の理由で、後からもう一度キャンセルする
            var cancelled = order.cancelByOperator("次の理由", true, false, OPERATOR, LATER.plusSeconds(60));

            // 確認：何もせず、最初の理由のまま
            assertThat(cancelled).isFalse();
            assertThat(order.cancellation().reason()).isEqualTo("最初の理由");
        }
    }

    @Nested
    @DisplayName("申請できるかのフラグ")
    class RequestableFlag {

        @Test
        @DisplayName("AC-95 「受付」でまだ申請していない注文は、キャンセルを申請できる")
        void acceptedIsRequestable() {
            // 操作と確認：申請できる
            assertThat(TestOrders.accepted(ORDERED_AT).isCancellationRequestable()).isTrue();
        }

        @Test
        @DisplayName("AC-96 申請中の注文は、申請できる操作に出さない")
        void requestedIsNotRequestable() {
            // 操作と確認：申請できない
            assertThat(TestOrders.requested(ORDERED_AT).isCancellationRequestable()).isFalse();
        }

        @Test
        @DisplayName("AC-96 却下された注文は、申請できる操作に出さず、集約も申請を拒む")
        void rejectedIsNotRequestable() {
            // 準備：申請が却下された注文
            var rejected = rejectedOrder();

            // 確認：申請できない
            assertThat(rejected.isCancellationRequestable()).isFalse();

            // 操作と確認：フラグのとおり、業務のエラー CANCELLATION_NOT_REQUESTABLE で拒否される
            assertBusinessError(() -> rejected.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }

        @Test
        @DisplayName("AC-95 運営者がキャンセルした注文は、申請できる操作に出さず、集約も申請を拒む")
        void cancelledIsNotRequestable() {
            // 準備：運営者がキャンセルした注文
            var cancelled = cancelledByOperatorOrder();

            // 確認：申請できない
            assertThat(cancelled.isCancellationRequestable()).isFalse();

            // 操作と確認：フラグのとおり、業務のエラー CANCELLATION_NOT_REQUESTABLE で拒否される
            assertBusinessError(() -> cancelled.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }
    }

    @Nested
    @DisplayName("承認・却下できるかのフラグ")
    class PendingFlag {

        @Test
        @DisplayName("AC-101、102 「キャンセル申請中」の注文は、承認・却下できる")
        void requestedIsPending() {
            // 操作と確認：承認・却下できる
            assertThat(TestOrders.requested(ORDERED_AT).isCancellationPending()).isTrue();
        }

        @Test
        @DisplayName("AC-101、102 申請のない「受付」の注文は、承認・却下できる操作に出さず、集約も却下を拒む")
        void acceptedIsNotPending() {
            // 準備：申請のない受付の注文
            var accepted = TestOrders.accepted(ORDERED_AT);

            // 確認：承認・却下できない
            assertThat(accepted.isCancellationPending()).isFalse();

            // 操作と確認：フラグのとおり、業務のエラー CANCELLATION_NOT_PENDING で拒否される
            assertBusinessError(() -> accepted.rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
        }

        @Test
        @DisplayName("AC-102 却下された注文は、承認・却下できる操作に出さない")
        void rejectedIsNotPending() {
            // 操作と確認：承認・却下できない
            assertThat(rejectedOrder().isCancellationPending()).isFalse();
        }
    }

    @Nested
    @DisplayName("運営者がキャンセルできるかのフラグ")
    class OperatorCancellableFlag {

        @Test
        @DisplayName("AC-103 「受付」の注文は、運営者がキャンセルできる")
        void acceptedIsCancellable() {
            // 準備：受付の注文
            var accepted = TestOrders.accepted(ORDERED_AT);

            // 確認（前提）：受付
            assertThat(accepted.status()).isEqualTo(OrderStatus.ACCEPTED);

            // 操作と確認：キャンセルできる
            assertThat(accepted.isCancellableByOperator(false)).isTrue();
        }

        @Test
        @DisplayName("AC-103 「キャンセル申請中」の注文は、運営者がキャンセルできる")
        void requestedIsCancellable() {
            // 準備：キャンセル申請中の注文
            var requested = TestOrders.requested(ORDERED_AT);

            // 確認（前提）：キャンセル申請中
            assertThat(requested.status()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);

            // 操作と確認：キャンセルできる
            assertThat(requested.isCancellableByOperator(false)).isTrue();
        }

        @Test
        @DisplayName("キャンセル済みの注文は、キャンセルしても何もしないので、運営者がキャンセルできる操作に出さない")
        void cancelledIsNotCancellable() {
            // 準備：運営者がキャンセルした注文
            var cancelled = cancelledByOperatorOrder();

            // 確認（前提）：キャンセル
            assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);

            // 操作と確認：キャンセルできない
            assertThat(cancelled.isCancellableByOperator(false)).isFalse();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 申請を {@link #LATER} に却下され、「受付」に戻った注文を作る。
     *
     * @return 却下された注文
     */
    private static Order rejectedOrder() {
        var order = TestOrders.requested(ORDERED_AT);
        order.rejectCancellation(OPERATOR, LATER);
        return order;
    }

    /**
     * 申請を {@link #LATER} に承認され、キャンセルになった注文を作る。
     *
     * <p>キャンセルのイベントは取り出してある。
     *
     * @return 承認でキャンセルになった注文
     */
    private static Order approvedOrder() {
        var order = TestOrders.requested(ORDERED_AT);
        order.approveCancellation(OPERATOR, LATER);
        order.pullEvents();
        return order;
    }

    /**
     * 運営者が {@link #LATER} にキャンセルした注文を作る。
     *
     * @return 運営者がキャンセルした注文
     */
    private static Order cancelledByOperatorOrder() {
        var order = TestOrders.accepted(ORDERED_AT);
        order.cancelByOperator(OPERATOR_REASON, true, false, OPERATOR, LATER);
        return order;
    }
}
