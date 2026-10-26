package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.order.Cancellation;
import com.ecsite.module.order.domain.order.CancellationKind;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.domain.order.Gender;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.AddressArea;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.jooq.DSLContext;
import com.ecsite.AsyncEvents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文のリポジトリの統合テスト。行のロックを確認するテスト以外は、テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class JooqOrderRepositoryTest {

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 注文番号の日付（ロールバックしないテストが作る注文と、他のテストの注文番号が重ならない日付）
     */
    private static final String ORDER_NUMBER_DATE = "19991231";

    @Autowired
    OrderRepository repository;

    @Autowired
    DSLContext dsl;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ApplicationEvents events;

    @Test
    @DisplayName("注文を保存し、明細・金額・お届け先・注文時の年齢と性別を含めて再構築できる。注文の状態の変化（支払待ち）のイベントが発行される")
    void savesAndRestoresOrder() {
        var order = order(UUID.randomUUID(), orderNumber());
        var expected = order.toSnapshot();

        assertThat(repository.insert(order)).isTrue();

        var restored = repository.findById(order.id()).orElseThrow();
        assertThat(restored.toSnapshot()).isEqualTo(expected);
        assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::orderId).containsExactly(order.id());
    }

    @Test
    @DisplayName("BR-10 注文番号が重複したら保存せずに false を返す（呼ぶ側が番号を再生成する）")
    void returnsFalseForDuplicateOrderNumber() {
        var number = orderNumber();
        repository.insert(order(UUID.randomUUID(), number));
        var duplicate = order(UUID.randomUUID(), number);

        assertThat(repository.insert(duplicate)).isFalse();

        assertThat(repository.findById(duplicate.id())).isEmpty();
        assertThat(events.stream(OrderStatusChanged.class)).hasSize(1);
    }

    @Test
    @DisplayName("AC-91 注文手続きの注文を引ける。注文手続き 1 つにつき注文は 1 つ")
    void findsOrderByCheckout() {
        var checkoutId = UUID.randomUUID();
        var order = order(checkoutId, orderNumber());
        repository.insert(order);

        assertThat(repository.findIdByCheckoutId(checkoutId)).contains(order.id());
        assertThat(repository.findIdByCheckoutId(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> repository.insert(order(checkoutId, orderNumber()))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("注文を受付に変えて更新すると、状態が受付になり、バージョンが 1 上がり、状態の変化のイベントが発行される")
    void updatesStatusAndVersion() {
        var order = order(UUID.randomUUID(), orderNumber());
        repository.insert(order);
        var loaded = repository.findById(order.id()).orElseThrow();
        loaded.acceptPayment(ORDERED_AT.plusSeconds(60));

        repository.update(loaded);

        var restored = repository.findById(order.id()).orElseThrow().toSnapshot();
        assertThat(restored.status()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(restored.version()).isEqualTo(order.toSnapshot().version() + 1);
        assertThat(restored).usingRecursiveComparison().ignoringFields("status", "version")
                .isEqualTo(order.toSnapshot());
        assertThat(events.stream(OrderStatusChanged.class)).hasSize(2);
    }

    @Test
    @DisplayName("キャンセルの申請と承認によるキャンセルを更新すると、申請と承認した運営者、キャンセルの記録を含めて再構築できる")
    void updatesCancellationRequestAndCancellation() {
        var order = order(UUID.randomUUID(), orderNumber());
        repository.insert(order);
        var accepted = repository.findById(order.id()).orElseThrow();
        accepted.acceptPayment(ORDERED_AT.plusSeconds(60));
        repository.update(accepted);
        var requested = repository.findById(order.id()).orElseThrow();
        var requestedAt = ORDERED_AT.plusSeconds(3600);
        requested.requestCancellation("サイズが合わない", requestedAt);
        repository.update(requested);
        assertThat(repository.findById(order.id()).orElseThrow().toSnapshot().cancellationRequest())
                .isEqualTo(new CancellationRequest(requestedAt, "サイズが合わない", CancellationRequest.Result.PENDING,
                        null, null));

        var operator = new Operator(UUID.randomUUID(), "管理者 花子");
        var decidedAt = ORDERED_AT.plusSeconds(7200);
        var pending = repository.lockById(order.id()).orElseThrow();
        assertThat(pending.approveCancellation(operator, decidedAt)).isTrue();
        repository.update(pending);

        var restored = repository.findById(order.id()).orElseThrow().toSnapshot();
        assertThat(restored.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(restored.cancellationRequest()).isEqualTo(new CancellationRequest(requestedAt, "サイズが合わない",
                CancellationRequest.Result.APPROVED, decidedAt, operator));
        assertThat(restored.cancellation()).isEqualTo(
                new Cancellation(decidedAt, CancellationKind.REQUEST_APPROVED, null, operator));
        assertThat(restored.version()).isEqualTo(3);
    }

    @Test
    @DisplayName("注文を行ロックして取得すると、保存した注文を再構築して返す。ない注文には空を返す")
    void locksOrderById() {
        var order = order(UUID.randomUUID(), orderNumber());
        repository.insert(order);

        assertThat(repository.lockById(order.id()).orElseThrow().toSnapshot()).isEqualTo(order.toSnapshot());
        assertThat(repository.lockById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("注文番号が使われているかを、保存した注文の番号だけ true で確認できる")
    void checksOrderNumberExistence() {
        var number = orderNumber();
        repository.insert(order(UUID.randomUUID(), number));

        assertThat(repository.existsByOrderNumber(OrderNumber.of(number))).isTrue();
        assertThat(repository.existsByOrderNumber(OrderNumber.of(otherThan(number)))).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("注文を行ロックして取得している間は、別のトランザクションが同じ注文の行をロックしようとすると失敗する")
    void blocksOtherTransactionWhileLocked() throws Exception {
        // ロールバックしないので、このテストで作った注文の行だけを使う
        var order = order(UUID.randomUUID(), orderNumber());
        transactionTemplate.executeWithoutResult(status -> repository.insert(order));
        var executor = Executors.newSingleThreadExecutor();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                assertThat(repository.lockById(order.id())).isPresent();

                var other = executor.submit(() -> transactionTemplate.executeWithoutResult(inner ->
                        dsl.selectFrom(SALES_ORDER).where(SALES_ORDER.ID.eq(order.id())).forUpdate().noWait().fetch()));

                assertThatThrownBy(() -> other.get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasMessageContaining("could not obtain lock");
            });
        } finally {
            executor.shutdownNow();
        }
    }

    // ============================== 補助 ==============================

    /**
     * 他のテストが使わない日付と、乱数の 6 桁で作った注文番号
     */
    private static String orderNumber() {
        return ORDER_NUMBER_DATE + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /**
     * 指定の注文番号とは別の注文番号
     */
    private static String otherThan(String orderNumber) {
        var next = (Integer.parseInt(orderNumber.substring(ORDER_NUMBER_DATE.length() + 1)) + 1) % 1_000_000;
        return ORDER_NUMBER_DATE + "-" + String.format("%06d", next);
    }

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
                ShippingAddress.of("山田 太郎", "100-0001", new AddressArea("13", "東京都", "13101", "千代田区"), "千代田 1-1",
                        "03-1234-5678"),
                new BuyerProfile(36, Gender.FEMALE));
    }
}
