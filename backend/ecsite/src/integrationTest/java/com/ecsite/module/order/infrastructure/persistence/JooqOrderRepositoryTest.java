package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.domain.order.BuyerProfile;
import com.ecsite.module.order.domain.order.Gender;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.OrderNumber;
import com.ecsite.module.order.domain.order.ShippingAddress;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.event.OrderStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文のリポジトリの統合テスト（T2309）。テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class JooqOrderRepositoryTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    OrderRepository repository;

    @Autowired
    ApplicationEvents events;

    @Test
    @DisplayName("注文を保存し、明細・金額・お届け先・注文時の年齢と性別を含めて再構築できる。注文の状態の変化（支払待ち）のイベントが発行される")
    void savesAndRestoresOrder() {
        var order = order(UUID.randomUUID(), "20261008-000001");
        var expected = order.toSnapshot();

        assertThat(repository.insert(order)).isTrue();

        var restored = repository.findById(order.id()).orElseThrow();
        assertThat(restored.toSnapshot()).isEqualTo(expected);
        assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::orderId).containsExactly(order.id());
    }

    @Test
    @DisplayName("BR-10 注文番号が重複したら保存せずに false を返す（呼ぶ側が番号を作り直す）")
    void returnsFalseForDuplicateOrderNumber() {
        repository.insert(order(UUID.randomUUID(), "20261008-000002"));
        var duplicate = order(UUID.randomUUID(), "20261008-000002");

        assertThat(repository.insert(duplicate)).isFalse();

        assertThat(repository.findById(duplicate.id())).isEmpty();
        assertThat(events.stream(OrderStatusChanged.class)).hasSize(1);
    }

    @Test
    @DisplayName("AC-91 注文手続きの注文を引ける。注文手続き 1 つにつき注文は 1 つ")
    void findsOrderByCheckout() {
        var checkoutId = UUID.randomUUID();
        var order = order(checkoutId, "20261008-000003");
        repository.insert(order);

        assertThat(repository.findIdByCheckoutId(checkoutId)).contains(order.id());
        assertThat(repository.findIdByCheckoutId(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> repository.insert(order(checkoutId, "20261008-000004"))).isInstanceOf(RuntimeException.class);
    }

    // ============================== 補助 ==============================

    /**
     * 2 つの税率の品目を持つ注文
     */
    private static Order order(UUID checkoutId, String orderNumber) {
        var shirt = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "TS-001", "TS-001-WM", "T シャツ",
                List.of(new AxisValue("色", "白"), new AxisValue("サイズ", "M")), Money.of(2700), UUID.randomUUID(),
                null, true, false);
        var tea = new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), "TEA-1", "TEA-1", "お茶", List.of(),
                Money.of(1234), UUID.randomUUID(), null, true, false);
        var items = List.of(
                QuoteItem.of(shirt, 1, 10, BigDecimal.TEN, Money.of(2970)),
                QuoteItem.of(tea, 1, 10, new BigDecimal("8"), Money.of(1332)));
        var amounts = new Amounts(Money.of(4302), Money.of(1000), Money.of(5302), List.of(
                new TaxAmount(BigDecimal.TEN, Money.of(3970), Money.of(360)),
                new TaxAmount(new BigDecimal("8"), Money.of(1332), Money.of(98))));
        return Order.place(UUID.randomUUID(), OrderNumber.of(orderNumber), checkoutId, UUID.randomUUID(), ORDERED_AT,
                new Quote(items, amounts, true), "taro@example.com",
                ShippingAddress.of("山田 太郎", "100-0001", "東京都", "千代田区千代田 1-1", "03-1234-5678"),
                new BuyerProfile(36, Gender.FEMALE));
    }
}
