package com.ecsite.module.order.domain.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文手続きの単体テスト
 */
class CheckoutTest {

    /**
     * 注文手続きを始める時刻
     */
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    /**
     * 注文手続きの有効時間
     */
    private static final Duration TTL = Duration.ofMinutes(15);

    /**
     * 支払待ちの注文 A の ID
     */
    private static final UUID ORDER_A = UUID.randomUUID();

    /**
     * 支払待ちの注文 B の ID
     */
    private static final UUID ORDER_B = UUID.randomUUID();

    @Test
    @DisplayName("AC-406-11 確認していない注文手続きは、支払待ちの注文がなければ確認済みとみなす")
    void unconfirmedCheckoutAcknowledgesEmptyPendingOrders() {
        var checkout = start(Set.of());

        assertThat(checkout.acknowledgedOrderIds()).isEmpty();
        assertThat(checkout.acknowledges(Set.of())).isTrue();
    }

    @Test
    @DisplayName("AC-406-11 確認していない注文手続きは、支払待ちの注文が 1 件でもあれば確認済みとみなさない")
    void unconfirmedCheckoutDoesNotAcknowledgePendingOrders() {
        var checkout = start(Set.of());

        assertThat(checkout.acknowledges(Set.of(ORDER_A))).isFalse();
    }

    @Test
    @DisplayName("AC-406-10 確認した集合に、今の支払待ちの注文が全て含まれていれば確認済み")
    void acknowledgesWhenAllPendingOrdersAreIncluded() {
        var checkout = start(Set.of(ORDER_A, ORDER_B));

        assertThat(checkout.acknowledges(Set.of(ORDER_A, ORDER_B))).isTrue();
        assertThat(checkout.acknowledges(Set.of(ORDER_A))).isTrue();
    }

    @Test
    @DisplayName("AC-406-12 確認した後に支払待ちの注文が増えたときは、確認済みとみなさない")
    void doesNotAcknowledgeNewlyPendingOrder() {
        var checkout = start(Set.of(ORDER_A));

        assertThat(checkout.acknowledges(Set.of(ORDER_A, ORDER_B))).isFalse();
    }

    @Test
    @DisplayName("AC-406-10 確認した集合は、始めたときの集合を後から変えても変わらない")
    void acknowledgedOrderIdsAreCopied() {
        var source = new HashSet<>(Set.of(ORDER_A));
        var checkout = start(source);

        source.add(ORDER_B);

        assertThat(checkout.acknowledgedOrderIds()).containsExactly(ORDER_A);
    }

    /**
     * 支払待ちの注文の集合を確認したものとして、注文手続きを始める
     *
     * @param acknowledgedOrderIds 確認した支払待ちの注文の ID
     * @return 始めた注文手続き
     */
    private static Checkout start(Set<UUID> acknowledgedOrderIds) {
        return Checkout.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, NOW, TTL, acknowledgedOrderIds);
    }
}
