package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.common.model.id.AssetId;
import com.ecsite.module.media.application.port.PendingAssetRepository;
import com.ecsite.module.media.domain.PendingAsset;
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ecsite.jooq.media.Tables.PENDING_ASSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * jOOQ の仮のアセットのリポジトリの統合テスト
 *
 * <p>行のロックを確認するテスト以外は、テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqPendingAssetRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 用意した時刻
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant PREPARED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 保存期間を過ぎたものを削除するテストの基準の時刻
     *
     * <p>表の全体から削除するので、ロールバックしない他のテストが残す仮のアセット（テストの時計の 2026 年）より前の時刻にする。
     */
    private static final Instant LONG_AGO = Instant.parse("1980-01-01T00:00:00Z");

    // ============================== Bean ==============================

    @Autowired
    PendingAssetRepository repository;

    @Autowired
    DSLContext dsl;

    @Autowired
    TransactionTemplate transactionTemplate;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("記録")
    class Insert {

        @Test
        @DisplayName("仮のアセットを記録すると、アセット ID・配信のキー・用意した時刻が保存される")
        void insertsPendingAsset() {
            // 準備：仮のアセット
            var pending = pending(new AssetId(UUID.randomUUID()), PREPARED_AT);

            // 操作：記録する
            repository.insert(pending);

            // 確認：全ての項目が保存されている
            var record = dsl.fetchOne(PENDING_ASSET, PENDING_ASSET.ID.eq(pending.id().value()));
            assertThat(record).isNotNull();
            assertThat(record.getStorageKey()).isEqualTo(pending.storageKey());
            assertThat(record.getPreparedAt()).isEqualTo(PREPARED_AT);
        }

        @Test
        @DisplayName("同じ配信のキーの仮のアセットは記録できない")
        void rejectsDuplicateStorageKey() {
            // 準備：記録した仮のアセット
            var first = pending(new AssetId(UUID.randomUUID()), PREPARED_AT);
            repository.insert(first);

            // 操作と確認：ID が違っても、配信のキーが同じなら一意の制約で拒否される
            assertThatThrownBy(() -> repository.insert(new PendingAsset(new AssetId(UUID.randomUUID()), first.storageKey(), PREPARED_AT)))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Nested
    @DisplayName("1 件の削除")
    class Delete {

        @Test
        @DisplayName("ある仮のアセットを削除すると、削除できたことを返し、他の仮のアセットは残る")
        void deletesPendingAsset() {
            // 準備：2 つの仮のアセット
            var deleted = pending(new AssetId(UUID.randomUUID()), PREPARED_AT);
            var kept = pending(new AssetId(UUID.randomUUID()), PREPARED_AT);
            repository.insert(deleted);
            repository.insert(kept);

            // 操作：1 つを削除する
            var result = repository.deleteById(deleted.id());

            // 確認：削除できたことを返し、指定しなかった 1 つだけが残る
            assertThat(result).isTrue();
            assertThat(exists(deleted.id())).isFalse();
            assertThat(exists(kept.id())).isTrue();
        }

        @Test
        @DisplayName("存在しない仮のアセットは削除できず、削除できなかったことを返す")
        void returnsFalseForMissing() {
            // 準備：1 つの仮のアセット
            var kept = pending(new AssetId(UUID.randomUUID()), PREPARED_AT);
            repository.insert(kept);

            // 操作：存在しない ID を削除する
            var result = repository.deleteById(new AssetId(UUID.randomUUID()));

            // 確認：削除できなかったことを返し、記録した仮のアセットは残る
            assertThat(result).isFalse();
            assertThat(exists(kept.id())).isTrue();
        }

        @Test
        @DisplayName("同じ仮のアセットを 2 回削除すると、2 回目は削除できなかったことを返す")
        void returnsFalseOnSecondDelete() {
            // 準備：1 つの仮のアセット
            var pending = pending(new AssetId(UUID.randomUUID()), PREPARED_AT);
            repository.insert(pending);

            // 操作：2 回削除する
            var first = repository.deleteById(pending.id());
            var second = repository.deleteById(pending.id());

            // 確認：1 回目だけが削除できたことを返す
            assertThat(first).isTrue();
            assertThat(second).isFalse();
        }
    }

    @Nested
    @DisplayName("保存期間を過ぎたものの削除")
    class DeleteExpired {

        @Test
        @DisplayName("用意した時刻が基準より前のものを、古い順に件数の上限まで削除し、削除した行を返す")
        void deletesOldestUpToLimit() {
            // 準備：基準より前の 3 つの仮のアセット（古い順に oldest、middle、newest。記録の順は古い順と違える）
            var newest = pending(new AssetId(UUID.randomUUID()), LONG_AGO.minus(Duration.ofHours(1)));
            var oldest = pending(new AssetId(UUID.randomUUID()), LONG_AGO.minus(Duration.ofHours(3)));
            var middle = pending(new AssetId(UUID.randomUUID()), LONG_AGO.minus(Duration.ofHours(2)));
            repository.insert(newest);
            repository.insert(oldest);
            repository.insert(middle);

            // 操作：上限 2 件で削除する
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 2);

            // 確認：古い 2 つの全ての項目を返し（上限より多く削除しない）、最も新しいものだけが残る
            // ※上限より多く削除する誤りは、選ぶ行を繰り返し評価する実行計画のときだけ起き、計画は表の行数や統計で変わるので、毎回は再現しない
            assertThat(deleted).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(oldest, middle);
            assertThat(exists(oldest.id())).isFalse();
            assertThat(exists(middle.id())).isFalse();
            assertThat(exists(newest.id())).isTrue();
        }

        @Test
        @DisplayName("用意した時刻が同じものは、アセット ID の順に削除する")
        void deletesInIdOrderForSameTime() {
            // 準備：基準より前の同じ時刻の 2 つの仮のアセット（ID の最後のバイトだけが違い、記録の順は ID の順と違える）
            var base = UUID.randomUUID();
            var smaller = pending(new AssetId(new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() & ~0xFFL)), LONG_AGO.minus(Duration.ofHours(1)));
            var larger = pending(new AssetId(new UUID(base.getMostSignificantBits(), (base.getLeastSignificantBits() & ~0xFFL) | 1L)), LONG_AGO.minus(Duration.ofHours(1)));
            repository.insert(larger);
            repository.insert(smaller);

            // 操作：上限 1 件で削除する
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 1);

            // 確認：ID の小さいほうを削除し、大きいほうが残る
            assertThat(deleted).extracting(PendingAsset::id).containsExactly(smaller.id());
            assertThat(exists(larger.id())).isTrue();
        }

        @Test
        @DisplayName("用意した時刻が基準ちょうどのものは削除せず、1 マイクロ秒前のものは削除する")
        void keepsPendingAssetAtBoundary() {
            // 準備：基準ちょうどと、1 マイクロ秒前の仮のアセット
            var atBoundary = pending(new AssetId(UUID.randomUUID()), LONG_AGO);
            var justBefore = pending(new AssetId(UUID.randomUUID()), LONG_AGO.minus(1, ChronoUnit.MICROS));
            repository.insert(atBoundary);
            repository.insert(justBefore);

            // 操作：削除する
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 10);

            // 確認：1 マイクロ秒前のものだけを削除する
            assertThat(deleted).extracting(PendingAsset::id).containsExactly(justBefore.id());
            assertThat(exists(atBoundary.id())).isTrue();
        }

        @Test
        @DisplayName("保存期間を過ぎたものがなければ、何も削除せず空を返す")
        void returnsEmptyWhenNothingExpired() {
            // 準備：基準より後の仮のアセット
            var kept = pending(new AssetId(UUID.randomUUID()), LONG_AGO.plus(Duration.ofHours(1)));
            repository.insert(kept);

            // 操作：削除する
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 10);

            // 確認：空を返し、記録が残る
            assertThat(deleted).isEmpty();
            assertThat(exists(kept.id())).isTrue();
        }
    }

    @Nested
    @DisplayName("行のロック")
    class RowLock {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("1 件を削除したトランザクションのコミットの前は、別のトランザクションの保存期間を過ぎたものの削除が、その行を待たずにスキップする")
        void expiredDeleteSkipsRowLockedByDelete() {
            // 準備：ロールバックしないので、このテストで作った、保存期間を過ぎた仮のアセットだけを確認する
            var pending = pending(new AssetId(UUID.randomUUID()), LONG_AGO.minus(Duration.ofHours(1)));
            transactionTemplate.executeWithoutResult(status -> repository.insert(pending));
            var executor = Executors.newSingleThreadExecutor();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：1 件を削除したまま、別のトランザクションで保存期間を過ぎたものを削除する
                    assertThat(repository.deleteById(pending.id())).isTrue();
                    var other = executor.submit(() -> transactionTemplate.execute(inner -> repository.deleteExpiredBefore(LONG_AGO, 10)));

                    // 確認：待たずに終わり、その行を返さない
                    assertThat(getWithin(other)).extracting(PendingAsset::id).doesNotContain(pending.id());
                });
            } finally {
                executor.shutdownNow();
            }

            // 確認：1 件の削除のコミットで、行が削除されている
            assertThat(exists(pending.id())).isFalse();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("保存期間を過ぎたものを削除したトランザクションのコミットを待ってから、別のトランザクションで 1 件を削除すると、削除できなかったことを返す")
        void deleteWaitsForExpiredDeleteAndReturnsFalse() throws Exception {
            // 準備：ロールバックしないので、このテストで作った、保存期間を過ぎた仮のアセットだけを確認する
            var pending = pending(new AssetId(UUID.randomUUID()), LONG_AGO.minus(Duration.ofHours(1)));
            transactionTemplate.executeWithoutResult(status -> repository.insert(pending));
            var executor = Executors.newSingleThreadExecutor();
            try {
                var other = transactionTemplate.execute(status -> {
                    // 操作：保存期間を過ぎたものを削除したまま、別のトランザクションで同じ行を 1 件削除する
                    assertThat(repository.deleteExpiredBefore(LONG_AGO, 10)).extracting(PendingAsset::id).contains(pending.id());
                    var future = executor.submit(() -> transactionTemplate.execute(inner -> repository.deleteById(pending.id())));

                    // 確認（前提）：別のトランザクションが、行のロックを待っている
                    await().atMost(AsyncEvents.TIMEOUT).until(this::waitingForPendingAssetLock);
                    assertThat(future).isNotDone();
                    return future;
                });

                // 確認：コミットの後に終わり、削除できなかったことを返す
                assertThat(getWithin(Objects.requireNonNull(other))).isFalse();
            } finally {
                executor.shutdownNow();
            }
        }

        /**
         * 仮のアセットの表の行のロックを待っている接続があるかを返す。
         *
         * @return あれば true
         */
        private boolean waitingForPendingAssetLock() {
            return dsl.fetchExists(DSL.selectOne().from("pg_stat_activity")
                    .where("wait_event_type = 'Lock'")
                    .and("query like '%pending_asset%'"));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 別のスレッドの結果を、タイムアウトまで待って返す。
     *
     * @param future 別のスレッドの処理
     * @param <T>    結果の型
     * @return 結果
     */
    private static <T> T getWithin(Future<T> future) {
        try {
            return future.get(AsyncEvents.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 仮のアセットの記録があるかを返す。
     *
     * @param id アセット ID
     * @return あれば true
     */
    private boolean exists(AssetId id) {
        return dsl.fetchExists(PENDING_ASSET, PENDING_ASSET.ID.eq(id.value()));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 仮のアセットを作る。
     *
     * <p>配信のキーは、ID ごとに別にする。
     *
     * @param id         アセット ID
     * @param preparedAt 用意した時刻
     * @return 仮のアセット
     */
    private static PendingAsset pending(AssetId id, Instant preparedAt) {
        return new PendingAsset(id, "assets/" + id.value() + ".webp", preparedAt);
    }
}
