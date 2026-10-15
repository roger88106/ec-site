package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.model.value.Operator;
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
 * 注文の支払による状態遷移の単体テスト（T7303、BR-14、AC-143、146、153、155、158）
 */
class OrderPaymentTest {

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
    private static final Operator OPERATOR = new Operator(UUID.randomUUID(), "管理 花子");

    @Test
    @DisplayName("AC-143 注文は「支払待ち」で作られる")
    void placesPendingPayment() {
        var order = pendingOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.isAwaitingPayment()).isTrue();
    }

    @Nested
    @DisplayName("与信の成功")
    class AcceptPayment {

        @Test
        @DisplayName("AC-146 支払待ち → 受付")
        void acceptsPendingOrder() {
            var order = pendingOrder();

            var changed = order.acceptPayment(LATER);

            assertThat(changed).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(order.isAwaitingPayment()).isFalse();
        }

        @Test
        @DisplayName("BR-17 受付で重ねて受けても、何も変わらない")
        void ignoresWhenAccepted() {
            var order = acceptedOrder();

            var changed = order.acceptPayment(LATER);

            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("キャンセルの後に受けても、キャンセルのまま")
        void ignoresWhenCancelled() {
            var order = pendingOrder();
            order.expirePayment(LATER);

            var changed = order.acceptPayment(LATER);

            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("支払の期限切れ")
    class ExpirePayment {

        @Test
        @DisplayName("AC-153、155 支払待ち → キャンセル（支払期限切れ）。運営者と理由を持たず、注文キャンセルのイベントが登録される")
        void cancelsPendingOrder() {
            var order = pendingOrder();

            var changed = order.expirePayment(LATER);

            assertThat(changed).isTrue();
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            var cancellation = order.cancellation();
            assertThat(cancellation.kind()).isEqualTo(CancellationKind.PAYMENT_EXPIRED);
            assertThat(cancellation.cancelledAt()).isEqualTo(LATER);
            assertThat(cancellation.reason()).isNull();
            assertThat(cancellation.cancelledBy()).isNull();
            assertThat(order.pullEvents()).singleElement().isInstanceOf(OrderCancelled.class);
        }

        @Test
        @DisplayName("受付の注文は、期限切れにしない")
        void ignoresWhenAccepted() {
            var order = acceptedOrder();

            var changed = order.expirePayment(LATER);

            assertThat(changed).isFalse();
            assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(order.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("NFR-7 重ねて受けても、イベントは 1 回だけ")
        void expiresOnlyOnce() {
            var order = pendingOrder();
            order.expirePayment(LATER);
            order.pullEvents();

            var changed = order.expirePayment(LATER);

            assertThat(changed).isFalse();
            assertThat(order.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("AC-158 支払待ちの注文のキャンセル")
    class CancelPending {

        @Test
        @DisplayName("消費者は申請できない")
        void cannotRequest() {
            var order = pendingOrder();

            assertThat(order.isCancellationRequestable()).isFalse();
            assertBusinessError(() -> order.requestCancellation(null, LATER), OrderErrorCode.CANCELLATION_NOT_REQUESTABLE);
        }

        @Test
        @DisplayName("運営者もキャンセルできない")
        void operatorCannotCancel() {
            var order = pendingOrder();

            assertThat(order.isCancellableByOperator(false)).isFalse();
            assertBusinessError(() -> order.cancelByOperator("理由", true, false, OPERATOR, LATER), OrderErrorCode.AWAITING_PAYMENT);
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("受付・キャンセル申請中は、運営者がキャンセルできる。キャンセル済みはできない旗にする")
        void operatorCancellableStates() {
            var accepted = acceptedOrder();
            var requested = acceptedOrder();
            requested.requestCancellation(null, LATER);
            var cancelled = acceptedOrder();
            cancelled.cancelByOperator("理由", true, false, OPERATOR, LATER);

            assertThat(accepted.isCancellableByOperator(false)).isTrue();
            assertThat(requested.isCancellableByOperator(false)).isTrue();
            assertThat(cancelled.isCancellableByOperator(false)).isFalse();
        }
    }

    // ============================== 補助 ==============================

    /**
     * 「支払待ち」の注文（成立のイベントは取り出し済み）
     */
    static Order pendingOrder() {
        var variant = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(),
                Money.of(1000), UUID.randomUUID(), null, true, false);
        var amounts = new Amounts(Money.of(1100), Money.of(1000), Money.of(2100),
                List.of(new TaxAmount(BigDecimal.TEN, Money.of(2100), Money.of(190))));
        var quote = new Quote(List.of(QuoteItem.of(variant, 1, 10, BigDecimal.TEN, Money.of(1100))), amounts, true);
        var order = Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(),
                ORDERED_AT, quote, "taro@example.com",
                ShippingAddress.of("山田 太郎", "1000001", "東京都", "千代田区", "0312345678"), BuyerProfile.UNKNOWN);
        order.pullEvents();
        return order;
    }

    /**
     * 「受付」の注文（与信の成功の後）
     */
    static Order acceptedOrder() {
        var order = pendingOrder();
        order.acceptPayment(ORDERED_AT.plusSeconds(60));
        order.pullEvents();
        return order;
    }
}
