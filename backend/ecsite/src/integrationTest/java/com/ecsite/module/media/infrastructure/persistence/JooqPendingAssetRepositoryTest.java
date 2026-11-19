package com.ecsite.module.media.infrastructure.persistence;

import com.ecsite.module.media.application.port.PendingAssetRepository;
import com.ecsite.module.media.domain.PendingAsset;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.ecsite.jooq.media.Tables.PENDING_ASSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * jOOQ の仮のアセットのリポジトリの統合テスト
 *
 * <p>テストごとにロールバックする。
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
     * 保存期間を過ぎたものを消すテストの基準の時刻
     *
     * <p>表の全体から消すので、ロールバックしない他のテストが残す仮のアセット（テストの時計の 2026 年）より前の時刻にする。
     */
    private static final Instant LONG_AGO = Instant.parse("1980-01-01T00:00:00Z");

    // ============================== Bean ==============================

    @Autowired
    PendingAssetRepository repository;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("記録")
    class Insert {

        @Test
        @DisplayName("仮のアセットを記録すると、アセット ID・配信のキー・用意した時刻が保存される")
        void insertsPendingAsset() {
            // 準備：仮のアセット
            var pending = pending(UUID.randomUUID(), PREPARED_AT);

            // 操作：記録する
            repository.insert(pending);

            // 確認：全ての項目が保存されている
            var record = dsl.fetchOne(PENDING_ASSET, PENDING_ASSET.ID.eq(pending.id()));
            assertThat(record).isNotNull();
            assertThat(record.getStorageKey()).isEqualTo(pending.storageKey());
            assertThat(record.getPreparedAt()).isEqualTo(PREPARED_AT);
        }

        @Test
        @DisplayName("同じ配信のキーの仮のアセットは記録できない")
        void rejectsDuplicateStorageKey() {
            // 準備：記録した仮のアセット
            var first = pending(UUID.randomUUID(), PREPARED_AT);
            repository.insert(first);

            // 操作と確認：ID が違っても、配信のキーが同じなら一意の制約で拒否される
            assertThatThrownBy(() -> repository.insert(new PendingAsset(UUID.randomUUID(), first.storageKey(), PREPARED_AT)))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Nested
    @DisplayName("1 件の削除")
    class Delete {

        @Test
        @DisplayName("ある仮のアセットを消すと、消えたことを返し、他の仮のアセットは残る")
        void deletesPendingAsset() {
            // 準備：2 つの仮のアセット
            var deleted = pending(UUID.randomUUID(), PREPARED_AT);
            var kept = pending(UUID.randomUUID(), PREPARED_AT);
            repository.insert(deleted);
            repository.insert(kept);

            // 操作：1 つを消す
            var result = repository.deleteById(deleted.id());

            // 確認：消えたことを返し、指定しなかった 1 つだけが残る
            assertThat(result).isTrue();
            assertThat(exists(deleted.id())).isFalse();
            assertThat(exists(kept.id())).isTrue();
        }

        @Test
        @DisplayName("ない仮のアセットは消せず、消せなかったことを返す")
        void returnsFalseForMissing() {
            // 準備：1 つの仮のアセット
            var kept = pending(UUID.randomUUID(), PREPARED_AT);
            repository.insert(kept);

            // 操作：ない ID を消す
            var result = repository.deleteById(UUID.randomUUID());

            // 確認：消せなかったことを返し、記録した仮のアセットは残る
            assertThat(result).isFalse();
            assertThat(exists(kept.id())).isTrue();
        }

        @Test
        @DisplayName("同じ仮のアセットを 2 回消すと、2 回目は消せなかったことを返す")
        void returnsFalseOnSecondDelete() {
            // 準備：1 つの仮のアセット
            var pending = pending(UUID.randomUUID(), PREPARED_AT);
            repository.insert(pending);

            // 操作：2 回消す
            var first = repository.deleteById(pending.id());
            var second = repository.deleteById(pending.id());

            // 確認：1 回目だけが消えたことを返す
            assertThat(first).isTrue();
            assertThat(second).isFalse();
        }
    }

    @Nested
    @DisplayName("保存期間を過ぎたものの削除")
    class DeleteExpired {

        @Test
        @DisplayName("用意した時刻が基準より前のものを、古い順に件数の上限まで消し、消した行を返す")
        void deletesOldestUpToLimit() {
            // 準備：基準より前の 3 つの仮のアセット（古い順に oldest、middle、newest。記録の順は古い順と違える）
            var newest = pending(UUID.randomUUID(), LONG_AGO.minus(Duration.ofHours(1)));
            var oldest = pending(UUID.randomUUID(), LONG_AGO.minus(Duration.ofHours(3)));
            var middle = pending(UUID.randomUUID(), LONG_AGO.minus(Duration.ofHours(2)));
            repository.insert(newest);
            repository.insert(oldest);
            repository.insert(middle);

            // 操作：上限 2 件で消す
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 2);

            // 確認：古い 2 つの全ての項目を返し、最も新しいものだけが残る
            assertThat(deleted).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(oldest, middle);
            assertThat(exists(oldest.id())).isFalse();
            assertThat(exists(middle.id())).isFalse();
            assertThat(exists(newest.id())).isTrue();
        }

        @Test
        @DisplayName("用意した時刻が同じものは、アセット ID の順に消す")
        void deletesInIdOrderForSameTime() {
            // 準備：基準より前の同じ時刻の 2 つの仮のアセット（ID の最後のバイトだけが違い、記録の順は ID の順と違える）
            var base = UUID.randomUUID();
            var smaller = pending(new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() & ~0xFFL), LONG_AGO.minus(Duration.ofHours(1)));
            var larger = pending(new UUID(base.getMostSignificantBits(), (base.getLeastSignificantBits() & ~0xFFL) | 1L), LONG_AGO.minus(Duration.ofHours(1)));
            repository.insert(larger);
            repository.insert(smaller);

            // 操作：上限 1 件で消す
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 1);

            // 確認：ID の小さいほうを消し、大きいほうが残る
            assertThat(deleted).extracting(PendingAsset::id).containsExactly(smaller.id());
            assertThat(exists(larger.id())).isTrue();
        }

        @Test
        @DisplayName("用意した時刻が基準ちょうどのものは消さず、1 マイクロ秒前のものは消す")
        void keepsPendingAssetAtBoundary() {
            // 準備：基準ちょうどと、1 マイクロ秒前の仮のアセット
            var atBoundary = pending(UUID.randomUUID(), LONG_AGO);
            var justBefore = pending(UUID.randomUUID(), LONG_AGO.minus(1, ChronoUnit.MICROS));
            repository.insert(atBoundary);
            repository.insert(justBefore);

            // 操作：消す
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 10);

            // 確認：1 マイクロ秒前のものだけを消す
            assertThat(deleted).extracting(PendingAsset::id).containsExactly(justBefore.id());
            assertThat(exists(atBoundary.id())).isTrue();
        }

        @Test
        @DisplayName("保存期間を過ぎたものがなければ、何も消さず空を返す")
        void returnsEmptyWhenNothingExpired() {
            // 準備：基準より後の仮のアセット
            var kept = pending(UUID.randomUUID(), LONG_AGO.plus(Duration.ofHours(1)));
            repository.insert(kept);

            // 操作：消す
            var deleted = repository.deleteExpiredBefore(LONG_AGO, 10);

            // 確認：空を返し、記録が残る
            assertThat(deleted).isEmpty();
            assertThat(exists(kept.id())).isTrue();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 仮のアセットの記録があるかを返す。
     *
     * @param id アセット ID
     * @return あれば true
     */
    private boolean exists(UUID id) {
        return dsl.fetchExists(PENDING_ASSET, PENDING_ASSET.ID.eq(id));
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
    private static PendingAsset pending(UUID id, Instant preparedAt) {
        return new PendingAsset(id, "assets/" + id + ".webp", preparedAt);
    }
}
