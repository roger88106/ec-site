package com.ecsite.module.order.domain.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文手続きの単体テスト
 *
 * <p>支払待ちの注文を確認済みとみなすかの判定（{@link Checkout#acknowledges}）だけを確認する。注文のときに拒否することは
 * {@code PlaceOrderPendingConfirmationApiTest} などの統合テストが確認する。
 */
class CheckoutTest {

    // ============================== 定数 ==============================

    /**
     * 注文手続きを始める時刻
     *
     * <p>日本時間の 2026-11-01 09:00。
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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("確認していない注文手続き")
    class Unconfirmed {

        @Test
        @DisplayName("AC-406-11 確認していない注文手続きは、支払待ちの注文がなければ確認済みとみなす")
        void acknowledgesEmptyPendingOrders() {
            // 準備：何も確認せずに始めた注文手続き
            var checkout = start(Set.of());

            // 確認（前提）：確認した注文がない
            assertThat(checkout.acknowledgedOrderIds()).isEmpty();

            // 操作と確認：支払待ちの注文がなければ確認済み
            assertThat(checkout.acknowledges(Set.of())).isTrue();
        }

        @Test
        @DisplayName("AC-406-11 確認していない注文手続きは、支払待ちの注文が 1 件でもあれば確認済みとみなさない")
        void doesNotAcknowledgePendingOrders() {
            // 準備：何も確認せずに始めた注文手続き
            var checkout = start(Set.of());

            // 操作と確認：支払待ちの注文が 1 件あれば確認済みでない
            assertThat(checkout.acknowledges(Set.of(ORDER_A))).isFalse();
        }
    }

    @Nested
    @DisplayName("確認した注文手続き")
    class Confirmed {

        @Test
        @DisplayName("AC-406-10 確認した集合と今の支払待ちの注文が同じなら、確認済み")
        void acknowledgesSameOrders() {
            // 準備：注文 A と B を確認して始めた注文手続き
            var checkout = start(Set.of(ORDER_A, ORDER_B));

            // 操作と確認：今も A と B が支払待ちなら確認済み
            assertThat(checkout.acknowledges(Set.of(ORDER_A, ORDER_B))).isTrue();
        }

        @Test
        @DisplayName("AC-406-10 確認した後に支払待ちの注文が減っても、確認済み")
        void acknowledgesFewerOrders() {
            // 準備：注文 A と B を確認して始めた注文手続き
            var checkout = start(Set.of(ORDER_A, ORDER_B));

            // 操作と確認：今は A だけが支払待ちでも確認済み
            assertThat(checkout.acknowledges(Set.of(ORDER_A))).isTrue();
        }

        @Test
        @DisplayName("AC-406-12 確認した後に支払待ちの注文が増えたときは、確認済みとみなさない")
        void doesNotAcknowledgeNewlyPendingOrder() {
            // 準備：注文 A だけを確認して始めた注文手続き
            var checkout = start(Set.of(ORDER_A));

            // 操作と確認：後から B も支払待ちになると確認済みでない
            assertThat(checkout.acknowledges(Set.of(ORDER_A, ORDER_B))).isFalse();
        }

        @Test
        @DisplayName("AC-406-10 確認した集合は、始めたときに渡した集合を後から変えても変わらない")
        void copiesAcknowledgedOrderIds() {
            // 準備：注文 A の集合を渡して始めた注文手続き
            var source = new HashSet<>(Set.of(ORDER_A));
            var checkout = start(source);

            // 操作：渡した集合に注文 B を加える
            source.add(ORDER_B);

            // 確認：確認した集合は A だけのまま
            assertThat(checkout.acknowledgedOrderIds()).containsExactly(ORDER_A);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 支払待ちの注文の集合を確認したものとして、注文手続きを始める。
     *
     * @param acknowledgedOrderIds 確認した支払待ちの注文の ID
     * @return 始めた注文手続き
     */
    private static Checkout start(Set<UUID> acknowledgedOrderIds) {
        return Checkout.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, NOW, TTL, acknowledgedOrderIds);
    }
}
