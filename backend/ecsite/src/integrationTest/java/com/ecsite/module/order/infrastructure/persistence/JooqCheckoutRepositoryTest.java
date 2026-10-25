package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文手続きのリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqCheckoutRepositoryTest {

    /**
     * 注文手続きを始めた時刻
     */
    private static final Instant STARTED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 引当の保持時間
     */
    private static final Duration TTL = Duration.ofMinutes(30);

    @Autowired
    CheckoutRepository repository;

    @Test
    @DisplayName("注文手続きを保存すると、カートのバージョンと期限を含めて ID から再構築できる。ない ID には空を返す")
    void savesAndRestoresCheckout() {
        var checkout = Checkout.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7, STARTED_AT, TTL);

        repository.insert(checkout);

        assertThat(repository.findById(checkout.id())).contains(checkout);
        assertThat(repository.lockById(checkout.id())).contains(checkout);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        assertThat(repository.lockById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("カートの注文手続きの ID を全て引け、ほかのカートの注文手続きは含まない")
    void findsIdsByCart() {
        var cartId = UUID.randomUUID();
        var first = checkout(cartId, STARTED_AT);
        var second = checkout(cartId, STARTED_AT.plusSeconds(60));
        repository.insert(first);
        repository.insert(second);
        repository.insert(checkout(UUID.randomUUID(), STARTED_AT));

        assertThat(repository.findIdsByCartId(cartId)).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(repository.findIdsByCartId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("カートの最新の注文手続きの ID は、作成が最も新しいもの。注文手続きがなければ空")
    void findsLatestIdByCart() {
        var cartId = UUID.randomUUID();
        var older = checkout(cartId, STARTED_AT);
        var newer = checkout(cartId, STARTED_AT.plusSeconds(60));
        repository.insert(newer);
        repository.insert(older);

        assertThat(repository.findLatestIdByCartId(cartId)).contains(newer.id());
        assertThat(repository.findLatestIdByCartId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("期限が指定した時刻より前の注文手続きだけを消す。ちょうどの時刻のものは残る")
    void deletesOnlyCheckoutsExpiredBefore() {
        var expired = checkout(UUID.randomUUID(), STARTED_AT);
        var onBoundary = checkout(UUID.randomUUID(), STARTED_AT.plusSeconds(60));
        var alive = checkout(UUID.randomUUID(), STARTED_AT.plusSeconds(120));
        repository.insert(expired);
        repository.insert(onBoundary);
        repository.insert(alive);

        var deleted = repository.deleteExpiredBefore(onBoundary.expiresAt(), 10);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(expired.id())).isEmpty();
        assertThat(repository.findById(onBoundary.id())).isPresent();
        assertThat(repository.findById(alive.id())).isPresent();
    }

    @Test
    @DisplayName("期限切れの注文手続きは、件数の上限までしか消さない")
    void deletesUpToLimit() {
        for (int i = 0; i < 3; i++) {
            repository.insert(checkout(UUID.randomUUID(), STARTED_AT.plusSeconds(i)));
        }
        var cutoff = STARTED_AT.plus(TTL).plusSeconds(10);

        assertThat(repository.deleteExpiredBefore(cutoff, 2)).isEqualTo(2);
        assertThat(repository.deleteExpiredBefore(cutoff, 2)).isEqualTo(1);
        assertThat(repository.deleteExpiredBefore(cutoff, 2)).isZero();
    }

    // ============================== 補助 ==============================

    private static Checkout checkout(UUID cartId, Instant startedAt) {
        return Checkout.start(UUID.randomUUID(), UUID.randomUUID(), cartId, 1, startedAt, TTL);
    }
}
