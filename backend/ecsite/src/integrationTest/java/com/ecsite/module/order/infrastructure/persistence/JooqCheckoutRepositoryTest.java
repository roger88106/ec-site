package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文手続きのリポジトリの統合テスト
 *
 * <p>保存と再構築、カートからの検索、期限切れの削除を確認する。片付けのジョブの流れは {@code CheckoutCleanupJobTest} が確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqCheckoutRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 注文手続きを始めた時刻
     */
    private static final Instant STARTED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 引当の保持時間
     */
    private static final Duration TTL = Duration.ofMinutes(30);

    /**
     * 削除の確認で使う注文手続きの開始時刻
     *
     * <p>片付けのジョブのテストが使う注文手続き（2000 年）より前にして、
     * 表の全体を対象にする削除の条件に他のテストのデータが当たらないようにする。
     */
    private static final Instant OLD_STARTED_AT = Instant.parse("1980-01-01T00:00:00Z");

    /**
     * 保存と再構築で使うカートのバージョン（既定の 1 と区別する）
     */
    private static final long CART_VERSION = 7;

    // ============================== Bean ==============================

    @Autowired
    CheckoutRepository repository;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存と再構築")
    class SaveAndRestore {

        @Test
        @DisplayName("注文手続きを保存すると、カートのバージョンと期限を含めて ID から再構築できる")
        void savesAndRestoresCheckout() {
            // 準備：カートのバージョン 7 の注文手続き
            var checkout = Checkout.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CART_VERSION, STARTED_AT, TTL, Set.of());

            // 操作：保存する
            repository.insert(checkout);

            // 確認：読むときも、ロックして読むときも、保存したものと等しい
            assertThat(repository.findById(checkout.id())).contains(checkout);
            assertThat(repository.lockById(checkout.id())).contains(checkout);
        }

        @Test
        @DisplayName("ない ID には空を返す")
        void returnsEmptyForUnknownId() {
            // 操作と確認：読むときも、ロックして読むときも空
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();
            assertThat(repository.lockById(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("カートからの検索")
    class FindByCart {

        @Test
        @DisplayName("カートの注文手続きの ID を全て引け、ほかのカートの注文手続きは含まない")
        void findsIdsByCart() {
            // 準備：同じカートの 2 つと、ほかのカートの 1 つ
            var cartId = UUID.randomUUID();
            var first = checkout(cartId, STARTED_AT);
            var second = checkout(cartId, STARTED_AT.plusSeconds(60));
            repository.insert(first);
            repository.insert(second);
            repository.insert(checkout(UUID.randomUUID(), STARTED_AT));

            // 操作と確認：同じカートの 2 つだけを引け、注文手続きのないカートは空
            assertThat(repository.findIdsByCartId(cartId)).containsExactlyInAnyOrder(first.id(), second.id());
            assertThat(repository.findIdsByCartId(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("カートの最新の注文手続きの ID は、作成が最も新しいもの")
        void findsLatestIdByCart() {
            // 準備：同じカートの 2 つを、新しい方から保存する
            var cartId = UUID.randomUUID();
            var older = checkout(cartId, STARTED_AT);
            var newer = checkout(cartId, STARTED_AT.plusSeconds(60));
            repository.insert(newer);
            repository.insert(older);

            // 操作と確認：保存の順でなく、作成の新しい方
            assertThat(repository.findLatestIdByCartId(cartId)).contains(newer.id());
        }

        @Test
        @DisplayName("注文手続きのないカートの最新の注文手続きの ID は空")
        void findsNoLatestIdForCartWithoutCheckout() {
            // 操作と確認：空
            assertThat(repository.findLatestIdByCartId(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("期限切れの削除")
    class DeleteExpired {

        @Test
        @DisplayName("期限が指定した時刻より前の注文手続きだけを消す。ちょうどの時刻のものは残る")
        void deletesOnlyCheckoutsExpiredBefore() {
            // 準備：期限が、指定する時刻の前、ちょうど、後の 3 つ
            var expired = checkout(UUID.randomUUID(), OLD_STARTED_AT);
            var onBoundary = checkout(UUID.randomUUID(), OLD_STARTED_AT.plusSeconds(60));
            var alive = checkout(UUID.randomUUID(), OLD_STARTED_AT.plusSeconds(120));
            repository.insert(expired);
            repository.insert(onBoundary);
            repository.insert(alive);

            // 操作：ちょうどの注文手続きの期限を指定して消す
            var deleted = repository.deleteExpiredBefore(onBoundary.expiresAt(), 10);

            // 確認：前の 1 つだけが消え、ちょうどと後は残る
            assertThat(deleted).isEqualTo(1);
            assertThat(repository.findById(expired.id())).isEmpty();
            assertThat(repository.findById(onBoundary.id())).isPresent();
            assertThat(repository.findById(alive.id())).isPresent();
        }

        @Test
        @DisplayName("期限切れの注文手続きは、件数の上限までしか消さない")
        void deletesUpToLimit() {
            // 準備：期限切れの 3 つ
            for (int i = 0; i < 3; i++) {
                repository.insert(checkout(UUID.randomUUID(), OLD_STARTED_AT.plusSeconds(i)));
            }
            var cutoff = OLD_STARTED_AT.plus(TTL).plusSeconds(10);

            // 操作と確認：上限 2 件で消すと、2 件、1 件、0 件の順に消える
            assertThat(repository.deleteExpiredBefore(cutoff, 2)).isEqualTo(2);
            assertThat(repository.deleteExpiredBefore(cutoff, 2)).isEqualTo(1);
            assertThat(repository.deleteExpiredBefore(cutoff, 2)).isZero();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 指定したカートと開始時刻の注文手続きを作る。
     *
     * @param cartId    カート ID
     * @param startedAt 始めた時刻
     * @return 注文手続き（保存しない）
     */
    private static Checkout checkout(UUID cartId, Instant startedAt) {
        return Checkout.start(UUID.randomUUID(), UUID.randomUUID(), cartId, 1, startedAt, TTL, Set.of());
    }
}
