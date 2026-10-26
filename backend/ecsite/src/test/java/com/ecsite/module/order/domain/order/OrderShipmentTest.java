package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.event.OrderShipped;
import com.ecsite.module.order.event.OrderStatusChanged;
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
 * 注文の出荷と、出荷済みの注文のキャンセルの単体テスト
 */
class OrderShipmentTest {

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
    private static final Operator OPERATOR = new Operator(UUID.randomUUID(), "管理 花子");

    @Nested
    @DisplayName("出荷")
    class Ship {

        @Test
        @DisplayName("AC-168 「受付」の注文を出荷すると「出荷済み」になり、注文の出荷のイベントを登録する")
        void shipsAcceptedOrder() {
            var order = acceptedOrder();

            order.ship(Carrier.YAMATO, "123456789012", LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(order.pullEvents()).filteredOn(OrderShipped.class::isInstance).singleElement().isInstanceOfSatisfying(OrderShipped.class, event -> {
                assertThat(event.orderId()).isEqualTo(order.id());
                assertThat(event.occurredAt()).isEqualTo(LATER);
                assertThat(event.orderNumber()).isEqualTo("20261008-000001");
                assertThat(event.email()).isEqualTo("taro@example.com");
                assertThat(event.carrier()).isEqualTo(OrderShipped.Carrier.YAMATO);
                assertThat(event.trackingNumber()).isEqualTo("123456789012");
            });
        }

        @Test
        @DisplayName("AC-170 「受付」でない注文（支払待ち、キャンセル申請中、キャンセル、出荷済み）は出荷できない")
        void rejectsOthers() {
            var requested = acceptedOrder();
            requested.requestCancellation(null, LATER);
            var cancelled = acceptedOrder();
            cancelled.cancelByOperator("在庫の不備", true, false, OPERATOR, LATER);

            for (var order : List.of(pendingOrder(), requested, cancelled, shippedOrder())) {
                assertBusinessError(() -> order.ship(Carrier.YAMATO, "123456789012", LATER), OrderErrorCode.ORDER_NOT_SHIPPABLE);
            }
        }
    }

    @Nested
    @DisplayName("AC-171、190、191 出荷済みの注文のキャンセル")
    class CancelShipped {

        @Test
        @DisplayName("AC-190、191 返送された注文はキャンセルでき、在庫に戻すかを記録する")
        void cancelsReturnedOrder() {
            var restocked = shippedOrder();
            var notRestocked = shippedOrder();

            assertThat(restocked.cancelByOperator("返送された", true, true, OPERATOR, LATER)).isTrue();
            assertThat(notRestocked.cancelByOperator("返送された", false, true, OPERATOR, LATER)).isTrue();

            assertThat(restocked.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(restocked.cancellation())
                    .isEqualTo(new Cancellation(LATER, CancellationKind.OPERATOR, "返送された", OPERATOR, true));
            assertThat(notRestocked.cancellation().restocked()).isFalse();
            assertThat(restocked.pullEvents()).singleElement().isInstanceOfSatisfying(OrderStatusChanged.class,
                    event -> assertThat(event.to()).isEqualTo(OrderStatusChanged.Status.CANCELLED));
        }

        @Test
        @DisplayName("AC-171 返送でない出荷済みの注文はキャンセルできない")
        void rejectsNotReturned() {
            assertBusinessError(() -> shippedOrder().cancelByOperator("理由", true, false, OPERATOR, LATER),
                    OrderErrorCode.SHIPPED_ORDER_NOT_RETURNED);
        }

        @Test
        @DisplayName("AC-191 出荷済みの注文のキャンセルでは、在庫に戻すかの指定が必須")
        void requiresRestockChoice() {
            assertBusinessError(() -> shippedOrder().cancelByOperator("理由", null, true, OPERATOR, LATER),
                    OrderErrorCode.RESTOCK_CHOICE_REQUIRED);
        }

        @Test
        @DisplayName("AC-191 出荷済みでない注文は、指定によらず在庫に戻す")
        void alwaysRestocksNotShipped() {
            var order = acceptedOrder();

            order.cancelByOperator("在庫の不備", false, false, OPERATOR, LATER);

            assertThat(order.cancellation().restocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("できる操作（画面に返すフラグ）")
    class Actions {

        @Test
        @DisplayName("AC-167、170 出荷の登録は「受付」の注文だけ")
        void shippable() {
            var requested = acceptedOrder();
            requested.requestCancellation(null, LATER);

            assertThat(acceptedOrder().isShippable()).isTrue();
            assertThat(pendingOrder().isShippable()).isFalse();
            assertThat(requested.isShippable()).isFalse();
            assertThat(shippedOrder().isShippable()).isFalse();
        }

        @Test
        @DisplayName("AC-171、190 出荷済みの注文の運営者のキャンセルは、返送のときだけ")
        void cancellableWhenReturned() {
            assertThat(shippedOrder().isCancellableByOperator(true)).isTrue();
            assertThat(shippedOrder().isCancellableByOperator(false)).isFalse();
            assertThat(acceptedOrder().isCancellableByOperator(false)).isTrue();
            assertThat(pendingOrder().isCancellableByOperator(true)).isFalse();
        }
    }

    // ============================== 補助 ==============================

    /**
     * 「支払待ち」の注文（成立のイベントは取り出し済み）
     */
    private static Order pendingOrder() {
        var variant = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "P-1", "P-1-1", "商品", List.of(),
                Money.of(1000), UUID.randomUUID(), null, true, false);
        var amounts = new Amounts(Money.of(1100), Money.of(1000), Money.of(2100),
                List.of(new TaxAmount(BigDecimal.TEN, Money.of(2100), Money.of(190))));
        var quote = new Quote(List.of(QuoteItem.of(variant, 1, 10, BigDecimal.TEN, Money.of(1100))), amounts, true);
        var order = Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(), ORDERED_AT,
                quote, "taro@example.com", ShippingAddress.of("山田 太郎", "1000001", TestAreas.CHIYODA, "千代田 1-1", "0312345678"), BuyerProfile.UNKNOWN);
        order.pullEvents();
        return order;
    }

    /**
     * 「受付」の注文（状態の変化のイベントは取り出し済み）
     */
    private static Order acceptedOrder() {
        var order = pendingOrder();
        order.acceptPayment(ORDERED_AT.plusSeconds(30));
        order.pullEvents();
        return order;
    }

    /**
     * 「出荷済み」の注文（出荷のイベントは取り出し済み）
     */
    private static Order shippedOrder() {
        var order = acceptedOrder();
        order.ship(Carrier.YAMATO, "123456789012", ORDERED_AT.plus(Duration.ofHours(1)));
        order.pullEvents();
        return order;
    }
}
