package com.ecsite.module.order.domain.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文手続きの単体テスト
 *
 * <p>支払待ちの注文を確認済みとみなすかの判定（{@link Checkout#acknowledges}）、引当の期限（作成の日時より後であること
 * を含む）、カートの変化と持ち主の判定を確認する。注文のときに拒否することは {@code PlaceOrderPendingConfirmationApiTest}
 * などの統合テストが確認する。
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

    @Nested
    @DisplayName("引当の期限")
    class Expiry {

        @Test
        @DisplayName("AC-82 注文手続きの期限は、始めた時刻に有効時間を加えた時刻")
        void expiresAfterTtl() {
            // 操作：注文手続きを始める
            var checkout = start(Set.of());

            // 確認：期限が開始の 15 分後
            assertThat(checkout.expiresAt()).isEqualTo(Instant.parse("2026-11-01T00:15:00Z"));
        }

        @Test
        @DisplayName("注文手続きの作成の時刻は、始めた時刻")
        void recordsCreatedAt() {
            // 操作：注文手続きを始める
            var checkout = start(Set.of());

            // 確認：作成の時刻が開始の時刻
            assertThat(checkout.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("引当の期限が作成の日時の 1 マイクロ秒後の注文手続きは、作れる")
        void acceptsExpiryJustAfterCreation() {
            // 準備：作成の日時の 1 マイクロ秒後の期限
            var expiresAt = NOW.plus(1, ChronoUnit.MICROS);

            // 操作：注文手続きを作る
            var checkout = checkoutExpiringAt(expiresAt);

            // 確認：渡した期限のまま
            assertThat(checkout.expiresAt()).isEqualTo(expiresAt);
        }

        @Test
        @DisplayName("引当の期限が作成の日時と同じ注文手続きは、作れず、メッセージに日時が出ない")
        void rejectsExpiryAtCreation() {
            // 操作と確認：例外になり、メッセージに日時がない
            assertThatThrownBy(() -> checkoutExpiringAt(NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(NOW.toString());
        }

        @Test
        @DisplayName("引当の期限が作成の日時の 1 マイクロ秒前の注文手続きは、作れず、メッセージに日時が出ない")
        void rejectsExpiryBeforeCreation() {
            // 準備：作成の日時の 1 マイクロ秒前の期限
            var expiresAt = NOW.minus(1, ChronoUnit.MICROS);

            // 操作と確認：例外になり、メッセージに日時がない
            assertThatThrownBy(() -> checkoutExpiringAt(expiresAt))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(NOW.toString())
                    .hasMessageNotContaining(expiresAt.toString());
        }

        /**
         * 作成の日時が {@link #NOW} で、指定の期限の注文手続きを、コンストラクターで作る。
         *
         * @param expiresAt 引当の期限
         * @return 注文手続き
         */
        private Checkout checkoutExpiringAt(Instant expiresAt) {
            return new Checkout(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, expiresAt, NOW, Set.of());
        }
    }

    @Nested
    @DisplayName("カートの変化の判定")
    class CartChange {

        @Test
        @DisplayName("始めたときと同じバージョンのカートは、変わっていないとみなす")
        void sameVersionIsUnchanged() {
            // 準備：バージョン 3 のカートで始めた注文手続き
            var checkout = startWithCartVersion(3);

            // 操作と確認：今もバージョン 3 なら変わっていない
            assertThat(checkout.cartChangedSince(3)).isFalse();
        }

        @Test
        @DisplayName("始めたときとバージョンが違うカートは、変わったとみなす")
        void otherVersionIsChanged() {
            // 準備：バージョン 3 のカートで始めた注文手続き
            var checkout = startWithCartVersion(3);

            // 操作と確認：今はバージョン 4 なら変わった
            assertThat(checkout.cartChangedSince(4)).isTrue();
        }
    }

    @Nested
    @DisplayName("持ち主の判定")
    class Owner {

        @Test
        @DisplayName("AC-406-17 始めた会員は、注文手続きの持ち主")
        void starterIsOwner() {
            // 準備：会員が始めた注文手続き
            var memberId = UUID.randomUUID();
            var checkout = Checkout.start(UUID.randomUUID(), memberId, UUID.randomUUID(), 1, NOW, TTL, Set.of());

            // 操作と確認：始めた会員は持ち主
            assertThat(checkout.isOwnedBy(memberId)).isTrue();
        }

        @Test
        @DisplayName("AC-406-17 他の会員は、注文手続きの持ち主でない")
        void otherMemberIsNotOwner() {
            // 準備：会員が始めた注文手続き
            var checkout = start(Set.of());

            // 操作と確認：他の会員は持ち主でない
            assertThat(checkout.isOwnedBy(UUID.randomUUID())).isFalse();
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

    /**
     * 指定のバージョンのカートで、注文手続きを始める。
     *
     * @param cartVersion 始めたときのカートのバージョン
     * @return 始めた注文手続き
     */
    private static Checkout startWithCartVersion(long cartVersion) {
        return Checkout.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), cartVersion, NOW, TTL, Set.of());
    }
}
