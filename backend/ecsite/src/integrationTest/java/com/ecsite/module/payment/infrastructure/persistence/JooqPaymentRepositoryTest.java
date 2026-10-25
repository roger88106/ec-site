package com.ecsite.module.payment.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.event.PaymentExpired;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支払のリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class JooqPaymentRepositoryTest {

    /**
     * 支払の期限
     */
    private static final Instant DEADLINE = Instant.parse("2026-10-08T15:30:00Z");

    @Autowired
    PaymentRepository repository;

    @Autowired
    ApplicationEvents events;

    @Test
    @DisplayName("支払を保存すると、金額・期限・未払いの状態で、ID・注文から再構築できる。ない ID・注文には空を返す")
    void savesAndRestoresPayment() {
        var payment = payment(UUID.randomUUID(), DEADLINE);

        assertThat(repository.insertIfAbsent(payment)).isTrue();

        assertThat(repository.findById(payment.id()).orElseThrow().toSnapshot()).isEqualTo(payment.toSnapshot());
        assertThat(repository.findByOrderId(payment.orderId()).orElseThrow().toSnapshot()).isEqualTo(payment.toSnapshot());
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        assertThat(repository.findByOrderId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("同じ注文の支払がすでにあれば、保存せずに false を返し、先の支払が残る")
    void returnsFalseForSecondPaymentOfSameOrder() {
        var orderId = UUID.randomUUID();
        var first = payment(orderId, DEADLINE);
        repository.insertIfAbsent(first);
        var second = payment(orderId, DEADLINE.plusSeconds(60));

        assertThat(repository.insertIfAbsent(second)).isFalse();

        assertThat(repository.findById(second.id())).isEmpty();
        assertThat(repository.findByOrderId(orderId).orElseThrow().id()).isEqualTo(first.id());
    }

    @Test
    @DisplayName("支払画面を開いて与信を受けた支払を更新すると、状態・画面・与信の参照・時刻を保存し、バージョンが 1 上がり、与信のイベントが発行される")
    void updatesAuthorizedPayment() {
        var payment = payment(UUID.randomUUID(), DEADLINE);
        repository.insertIfAbsent(payment);
        var authorizedAt = DEADLINE.minusSeconds(600);
        var loaded = repository.lockById(payment.id()).orElseThrow();
        loaded.openPage(new PaymentPage("page-1", "https://pay.example.com/page-1"));
        loaded.authorize("auth-1", authorizedAt);

        repository.update(loaded);

        var restored = repository.findById(payment.id()).orElseThrow().toSnapshot();
        assertThat(restored.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(restored.page()).isEqualTo(new PaymentPage("page-1", "https://pay.example.com/page-1"));
        assertThat(restored.authorizationRef()).isEqualTo("auth-1");
        assertThat(restored.authorizedAt()).isEqualTo(authorizedAt);
        assertThat(restored.version()).isEqualTo(payment.toSnapshot().version() + 1);
        assertThat(events.stream(PaymentAuthorized.class)).extracting(PaymentAuthorized::orderId)
                .containsExactly(payment.orderId());
    }

    @Test
    @DisplayName("期限切れにした支払を更新すると、支払われなかった状態になり、期限切れのイベントが発行される")
    void updatesExpiredPayment() {
        var payment = payment(UUID.randomUUID(), DEADLINE);
        repository.insertIfAbsent(payment);
        var loaded = repository.findById(payment.id()).orElseThrow();
        assertThat(loaded.expire(DEADLINE.plusSeconds(1))).isTrue();

        repository.update(loaded);

        assertThat(repository.findById(payment.id()).orElseThrow().status()).isEqualTo(PaymentStatus.NOT_PAID);
        assertThat(events.stream(PaymentExpired.class)).extracting(PaymentExpired::orderId)
                .containsExactly(payment.orderId());
    }

    @Test
    @DisplayName("支払を、ID・注文・支払画面の ID で行ロックして取得できる。ないものには空を返す")
    void locksPayment() {
        var payment = payment(UUID.randomUUID(), DEADLINE);
        repository.insertIfAbsent(payment);
        var loaded = repository.findById(payment.id()).orElseThrow();
        loaded.openPage(new PaymentPage("page-2", "https://pay.example.com/page-2"));
        repository.update(loaded);

        assertThat(repository.lockById(payment.id()).orElseThrow().id()).isEqualTo(payment.id());
        assertThat(repository.lockByOrderId(payment.orderId()).orElseThrow().id()).isEqualTo(payment.id());
        assertThat(repository.lockByPageId("page-2").orElseThrow().id()).isEqualTo(payment.id());
        assertThat(repository.lockById(UUID.randomUUID())).isEmpty();
        assertThat(repository.lockByOrderId(UUID.randomUUID())).isEmpty();
        assertThat(repository.lockByPageId("page-none")).isEmpty();
    }

    @Test
    @DisplayName("注文の支払をまとめて取得でき、支払のない注文は含まない。注文がなければ空")
    void findsPaymentsByOrderIds() {
        var first = payment(UUID.randomUUID(), DEADLINE);
        var second = payment(UUID.randomUUID(), DEADLINE);
        repository.insertIfAbsent(first);
        repository.insertIfAbsent(second);
        repository.insertIfAbsent(payment(UUID.randomUUID(), DEADLINE));

        var found = repository.findByOrderIds(List.of(first.orderId(), second.orderId(), UUID.randomUUID()));

        assertThat(found).extracting(Payment::id).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(repository.findByOrderIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("期限を過ぎた未払いの支払を期限の古い順に上限まで取得し、期限内・未払いでない・与信の通知を待つ支払は除く")
    void findsUnpaidPastDeadline() {
        // 表の全体から期限切れを引くので、ロールバックしない他のテストの支払（2026 年）に当たらない過去の時刻にする
        var now = Instant.parse("2000-01-01T00:00:00Z");
        var oldest = payment(UUID.randomUUID(), now.minusSeconds(300));
        var older = payment(UUID.randomUUID(), now.minusSeconds(200));
        var onDeadline = payment(UUID.randomUUID(), now);
        var future = payment(UUID.randomUUID(), now.plusSeconds(1));
        var awaiting = payment(UUID.randomUUID(), now.minusSeconds(250));
        var expired = payment(UUID.randomUUID(), now.minusSeconds(100));
        for (var payment : List.of(older, oldest, onDeadline, future, awaiting, expired)) {
            repository.insertIfAbsent(payment);
        }
        var awaitingLoaded = repository.findById(awaiting.id()).orElseThrow();
        assertThat(awaitingLoaded.awaitAuthorization(now.minusSeconds(10))).isTrue();
        repository.update(awaitingLoaded);
        var expiredLoaded = repository.findById(expired.id()).orElseThrow();
        expiredLoaded.expire(now);
        repository.update(expiredLoaded);

        assertThat(repository.findUnpaidPastDeadline(now, 10)).extracting(Payment::id)
                .containsExactly(oldest.id(), older.id(), onDeadline.id());
        assertThat(repository.findUnpaidPastDeadline(now, 2)).extracting(Payment::id)
                .containsExactly(oldest.id(), older.id());
    }

    // ============================== 補助 ==============================

    /**
     * 指定した注文と期限の未払いの支払を作る
     */
    private static Payment payment(UUID orderId, Instant deadline) {
        return Payment.create(UUID.randomUUID(), orderId, Money.of(5302), deadline);
    }
}
