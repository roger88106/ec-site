package com.ecsite.module.member.application;

import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会員 ID を求める処理の単体テスト
 *
 * <p>キャッシュの使い方、会員の作成、確定の後の登録を、Spring を起動せずに確認する。
 */
class MemberIdResolverTest {

    // ============================== 定数 ==============================

    /**
     * 会員の、IdP のアカウントの ID
     */
    private static final String SUBJECT = "subject-1";

    // ============================== Bean ==============================

    /**
     * 会員の記録（IdP の subject → 会員 ID）を持つだけのリポジトリの中身
     */
    private final Map<String, UUID> members = new HashMap<>();

    /**
     * 会員 ID を探す呼び出しの回数
     */
    private int lookups;

    /**
     * 会員を記録する呼び出しの回数
     */
    private int inserts;

    /**
     * 会員 ID のキャッシュ
     */
    private final MemberIdCache cache = new MemberIdCache(new MemberIdCacheProperties(10_000, Duration.ofHours(1)));

    /**
     * 会員の記録を読み書きするリポジトリ
     */
    private final MemberRepository repository = new MemberRepository() {
        @Override
        public Optional<UUID> findIdByIdpSubject(String idpSubject) {
            lookups++;
            return Optional.ofNullable(members.get(idpSubject));
        }

        @Override
        public void insertIfAbsent(Member member) {
            inserts++;
            members.putIfAbsent(member.idpSubject(), member.id());
        }
    };

    /**
     * テストの対象
     */
    private final MemberIdResolver resolver = new MemberIdResolver(new MemberQueryService(repository),
            new MemberCommandService(repository, Clock.systemUTC()), cache);

    /**
     * トランザクションの同期を無効に戻す
     */
    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("同じアカウントで 2 回求めるとき")
    class Cached {

        @Test
        @DisplayName("DC-557-1 会員を作った後の 2 回目は、会員のリポジトリを呼ばない")
        void secondCallAfterCreationSkipsRepository() {
            // 準備：初めての会員を作った
            var first = resolver.resolve(SUBJECT);
            var lookupsAfterFirst = lookups;
            var insertsAfterFirst = inserts;

            // 操作：もう一度求める
            var second = resolver.resolve(SUBJECT);

            // 確認：同じ会員 ID で、リポジトリを呼んでいない
            assertThat(second).isEqualTo(first);
            assertThat(lookups).isEqualTo(lookupsAfterFirst);
            assertThat(inserts).isEqualTo(insertsAfterFirst);
        }

        @Test
        @DisplayName("DC-557-1 既にいた会員の 2 回目は、会員のリポジトリを呼ばない")
        void secondCallForExistingMemberSkipsRepository() {
            // 準備：会員がいて、一度求めた
            var existing = UUID.randomUUID();
            members.put(SUBJECT, existing);
            assertThat(resolver.resolve(SUBJECT)).isEqualTo(existing);
            var lookupsAfterFirst = lookups;

            // 操作：もう一度求める
            var second = resolver.resolve(SUBJECT);

            // 確認：同じ会員 ID で、リポジトリを呼んでいない
            assertThat(second).isEqualTo(existing);
            assertThat(lookups).isEqualTo(lookupsAfterFirst);
            assertThat(inserts).isZero();
        }

        @Test
        @DisplayName("別のアカウントは、キャッシュにあっても別に探す")
        void otherSubjectIsLookedUp() {
            // 準備：1 つ目のアカウントで求めた
            var first = resolver.resolve(SUBJECT);
            var lookupsAfterFirst = lookups;

            // 操作：別のアカウントで求める
            var second = resolver.resolve("subject-2");

            // 確認：別の会員 ID で、リポジトリを呼んでいる
            assertThat(second).isNotEqualTo(first);
            assertThat(lookups).isGreaterThan(lookupsAfterFirst);
        }
    }

    @Nested
    @DisplayName("トランザクションの同期が有効なとき")
    class InTransaction {

        @Test
        @DisplayName("確定の前はキャッシュに入れず、確定の後に入れる")
        void putsAfterCommitOnly() {
            // 準備：トランザクションの同期を有効にする
            TransactionSynchronizationManager.initSynchronization();

            // 操作：会員 ID を求める
            var id = resolver.resolve(SUBJECT);

            // 確認：確定の前は、キャッシュにない
            assertThat(cache.find(SUBJECT)).isEmpty();

            // 操作：確定する
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // 確認：確定の後は、キャッシュにある
            assertThat(cache.find(SUBJECT)).contains(id);
        }

        @Test
        @DisplayName("ロールバックしたときは、キャッシュに入れない")
        void doesNotPutOnRollback() {
            // 準備：トランザクションの同期を有効にする
            TransactionSynchronizationManager.initSynchronization();

            // 操作：会員 ID を求め、ロールバックする
            resolver.resolve(SUBJECT);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // 確認：キャッシュにない
            assertThat(cache.find(SUBJECT)).isEmpty();
        }
    }
}
