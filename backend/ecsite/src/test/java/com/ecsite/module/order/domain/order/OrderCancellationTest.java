package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.order.CancellationRequest.Result;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.event.OrderCancelled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文のキャンセルの状態遷移の単体テスト（T2401、BR-9、AC-95、96、101〜103、105）
 */
class OrderCancellationTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 申請・判断の日時
     */
    private static final Instant LATER = ORDERED_AT.plus(Duration.ofHours(1));

    /**
     * 判断・キャンセルした運営者（AC-142）
     */
    private static final Operator OPERATOR = new Operator(UUID.randomUUID(), "管理 花子");

    @Nested
    @DisplayName("AC-95、96 キャンセルの申請")
    class Request {

        @Test
        @DisplayName("AC-95 「受付」の注文を申請すると「キャンセル申請中」になる。理由を添えられる")
        void requests() {
            var order = acceptedOrder();

            order.requestCancellation("サイズを間違えました", LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_REQUESTED);
            assertThat(order.cancellationRequest())
                    .isEqualTo(new CancellationRequest(LATER, "サイズを間違えました", Result.PENDING, null, null));
        }

        @Test
        @DisplayName("AC-96 申請中の注文にもう一度申請しても、何も変わらない")
        void ignoresRepeatedRequest() {
            var order = acceptedOrder();
            order.requestCancellation("最初の理由", LATER);

            order.requestCancellation("次の理由", LATER.plusSeconds(60));

            assertThat(order.cancellationRequest().reason()).isEqualTo("最初の理由");
            assertThat(order.cancellationRequest().requestedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("AC-96 却下された注文、キャンセル済みの注文は申請できない（申請は 1 回だけ）")
        void rejectsSecondRequest() {
            var rejected = acceptedOrder();
            rejected.requestCancellation(null, LATER);
            rejected.rejectCancellation(OPERATOR, LATER);
            var cancelled = acceptedOrder();
            cancelled.cancelByOperator("在庫の不備", OPERATOR, LATER);

            assertBusinessError(() -> rejected.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
            assertBusinessError(() -> cancelled.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }

        @Test
        @DisplayName("AC-95 理由は 500 文字まで。空白だけの理由は理由なしとして扱う")
        void validatesReason() {
            assertBusinessError(() -> acceptedOrder().requestCancellation("あ".repeat(501), LATER),
                    OrderErrorCode.INVALID_CANCELLATION_REASON);
            var order = acceptedOrder();
            order.requestCancellation(" ", LATER);
            assertThat(order.cancellationRequest().reason()).isNull();
        }
    }

    @Nested
    @DisplayName("AC-101、102、105 申請の承認・却下")
    class Decide {

        @Test
        @DisplayName("AC-101 申請を承認するとキャンセルになり、在庫を戻すべきことと、注文キャンセルのイベントを返す")
        void approves() {
            var order = requestedOrder();

            var cancelled = order.approveCancellation(OPERATOR, LATER);

            assertThat(cancelled).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellationRequest().result()).isEqualTo(Result.APPROVED);
            assertThat(order.cancellationRequest().decidedAt()).isEqualTo(LATER);
            assertThat(order.cancellation()).isEqualTo(new Cancellation(LATER, false, null, OPERATOR));
            assertThat(order.pullEvents()).singleElement().isInstanceOf(OrderCancelled.class);
        }

        @Test
        @DisplayName("AC-102 申請を却下すると「受付」に戻る")
        void rejects() {
            var order = requestedOrder();

            order.rejectCancellation(OPERATOR, LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(order.cancellationRequest().result()).isEqualTo(Result.REJECTED);
            assertThat(order.cancellationRequest().decidedAt()).isEqualTo(LATER);
            assertThat(order.cancellationRequest().decidedBy()).isEqualTo(OPERATOR);
            assertThat(order.cancellation()).isNull();
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文の承認は何もせず、在庫も戻さない。却下はできない")
        void handlesCancelledOrder() {
            var order = requestedOrder();
            order.approveCancellation(OPERATOR, LATER);
            order.pullEvents();

            assertThat(order.approveCancellation(OPERATOR, LATER.plusSeconds(60))).isFalse();
            assertThat(order.cancellation().cancelledAt()).isEqualTo(LATER);
            assertThat(order.pullEvents()).isEmpty();
            assertBusinessError(() -> order.rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
        }

        @Test
        @DisplayName("申請のない「受付」の注文は、承認も却下もできない")
        void rejectsDecisionWithoutRequest() {
            assertBusinessError(() -> acceptedOrder().approveCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
            assertBusinessError(() -> acceptedOrder().rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("AC-103、105 運営者の判断によるキャンセル")
    class CancelByOperator {

        @Test
        @DisplayName("AC-103 「受付」の注文を、理由を記録してキャンセルできる")
        void cancelsAcceptedOrder() {
            var order = acceptedOrder();

            assertThat(order.cancelByOperator("在庫の不備", OPERATOR, LATER)).isTrue();

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellation()).isEqualTo(new Cancellation(LATER, true, "在庫の不備", OPERATOR));
            assertThat(order.pullEvents()).singleElement().isInstanceOf(OrderCancelled.class);
        }

        @Test
        @DisplayName("AC-103 「キャンセル申請中」の注文もキャンセルでき、申請は承認されたものとする")
        void cancelsRequestedOrder() {
            var order = requestedOrder();

            order.cancelByOperator("お客様の申請による", OPERATOR, LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancellationRequest().result()).isEqualTo(Result.APPROVED);
            assertThat(order.cancellation().kind()).isEqualTo(CancellationKind.OPERATOR);
        }

        @Test
        @DisplayName("AC-103 理由は必須で 500 文字まで")
        void requiresReason() {
            assertBusinessError(() -> acceptedOrder().cancelByOperator(" ", OPERATOR, LATER), OrderErrorCode.CANCELLATION_REASON_REQUIRED);
            assertBusinessError(() -> acceptedOrder().cancelByOperator(null, OPERATOR, LATER), OrderErrorCode.CANCELLATION_REASON_REQUIRED);
            assertBusinessError(() -> acceptedOrder().cancelByOperator("あ".repeat(501), OPERATOR, LATER),
                    OrderErrorCode.INVALID_CANCELLATION_REASON);
        }

        @Test
        @DisplayName("AC-105 キャンセル済みの注文へのキャンセルは何もせず、理由も変えない")
        void ignoresCancelledOrder() {
            var order = acceptedOrder();
            order.cancelByOperator("最初の理由", OPERATOR, LATER);

            assertThat(order.cancelByOperator("次の理由", OPERATOR, LATER.plusSeconds(60))).isFalse();

            assertThat(order.cancellation().reason()).isEqualTo("最初の理由");
        }
    }

    @Nested
    @DisplayName("できる操作（画面に返す旗）")
    class Actions {

        @Test
        @DisplayName("AC-95 「受付」でまだ申請していない注文だけ、キャンセルを申請できる")
        void requestable() {
            var accepted = acceptedOrder();
            var requested = requestedOrder();
            var rejected = requestedOrder();
            rejected.rejectCancellation(OPERATOR, LATER);
            var cancelled = acceptedOrder();
            cancelled.cancelByOperator("在庫の不備", OPERATOR, LATER);

            assertThat(accepted.isCancellationRequestable()).isTrue();
            assertThat(requested.isCancellationRequestable()).isFalse();
            assertThat(rejected.isCancellationRequestable()).isFalse();
            assertThat(cancelled.isCancellationRequestable()).isFalse();
            // 申請できないとした注文は、集約も申請を拒む（申請中は何もしないので出さない）
            assertBusinessError(() -> rejected.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
            assertBusinessError(() -> cancelled.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }

        @Test
        @DisplayName("AC-101、102 申請の承認・却下は「キャンセル申請中」の注文だけ")
        void pending() {
            var rejected = requestedOrder();
            rejected.rejectCancellation(OPERATOR, LATER);

            assertThat(requestedOrder().isCancellationPending()).isTrue();
            assertThat(acceptedOrder().isCancellationPending()).isFalse();
            assertThat(rejected.isCancellationPending()).isFalse();
            assertBusinessError(() -> acceptedOrder().rejectCancellation(OPERATOR, LATER), OrderErrorCode.CANCELLATION_NOT_PENDING);
        }

        @Test
        @DisplayName("AC-103 運営者のキャンセルは「キャンセル」でない注文だけ（キャンセル済みは何もしないので出さない）")
        void cancellable() {
            var cancelled = acceptedOrder();
            cancelled.cancelByOperator("在庫の不備", OPERATOR, LATER);

            assertThat(acceptedOrder().isCancelled()).isFalse();
            assertThat(requestedOrder().isCancelled()).isFalse();
            assertThat(cancelled.isCancelled()).isTrue();
        }
    }

    // ============================== 補助 ==============================

    /**
     * 「受付」の注文（与信の成功の後。成立のイベントは取り出し済み）
     */
    private static Order acceptedOrder() {
        var variant = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(),
                Money.of(1000), UUID.randomUUID(), null, true, false);
        var amounts = new Amounts(Money.of(1100), Money.of(1000), Money.of(2100),
                List.of(new TaxAmount(BigDecimal.TEN, Money.of(2100), Money.of(190))));
        var quote = new Quote(List.of(QuoteItem.of(variant, 1, 10, BigDecimal.TEN, Money.of(1100))), amounts, true);
        var order = Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(), ORDERED_AT,
                quote, "taro@example.com", ShippingAddress.of("山田 太郎", "1000001", "東京都", "千代田区", "0312345678"));
        order.acceptPayment(ORDERED_AT.plusSeconds(30));
        order.pullEvents();
        return order;
    }

    /**
     * 「キャンセル申請中」の注文
     */
    private static Order requestedOrder() {
        var order = acceptedOrder();
        order.requestCancellation("理由", ORDERED_AT.plusSeconds(60));
        return order;
    }
}
