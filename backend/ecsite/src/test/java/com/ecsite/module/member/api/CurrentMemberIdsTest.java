package com.ecsite.module.member.api;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.application.MemberCommandService;
import com.ecsite.module.member.application.MemberIdCache;
import com.ecsite.module.member.application.MemberIdCacheProperties;
import com.ecsite.module.member.application.MemberQueryService;
import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 現在の会員の ID を求める部品の単体テスト
 *
 * <p>ログインの状態から会員 ID を求めることと、初めての会員を作ることだけを確認する。token の検証と会員の API は、
 * 統合テストが確認する。
 */
class CurrentMemberIdsTest {

    // ============================== 定数 ==============================

    /**
     * ログインする会員の、IdP のアカウントの ID
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
    private final CurrentMemberIds currentMemberIds = new CurrentMemberIds(new CurrentMember(),
            new MemberQueryService(repository), new MemberCommandService(repository, Clock.systemUTC()), cache);

    /**
     * ログインの状態を消し、トランザクションの同期を無効に戻す
     */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("ログインしているとき")
    class LoggedIn {

        @Test
        @DisplayName("初めての会員は、会員を作り、その会員 ID を返す")
        void createsMemberOnFirstRequire() {
            // 準備：まだ会員のない IdP のアカウントでログインする
            loginAs(SUBJECT);

            // 操作：会員 ID を求める
            var id = currentMemberIds.require();

            // 確認：作った会員の ID
            assertThat(id).isEqualTo(members.get(SUBJECT));
        }

        @Test
        @DisplayName("2 回目も、同じ会員 ID を返す")
        void returnsSameMemberIdAgain() {
            // 準備：ログインし、一度会員 ID を求めた
            loginAs(SUBJECT);
            var first = currentMemberIds.require();

            // 操作と確認：もう一度求めても同じ
            assertThat(currentMemberIds.require()).isEqualTo(first);
        }

        @Test
        @DisplayName("会員がいれば、find も同じ会員 ID を返す")
        void findReturnsMemberId() {
            // 準備：ログインし、会員を作った
            loginAs(SUBJECT);
            var id = currentMemberIds.require();

            // 操作と確認：find も同じ ID
            assertThat(currentMemberIds.find()).contains(id);
        }
    }

    @Nested
    @DisplayName("同じアカウントで 2 回求めるとき")
    class Cached {

        @Test
        @DisplayName("DC-557-1 会員を作った後の 2 回目は、会員のリポジトリを呼ばない")
        void secondCallAfterCreationSkipsRepository() {
            // 準備：ログインし、初めての会員を作った
            loginAs(SUBJECT);
            var first = currentMemberIds.require();
            var lookupsAfterFirst = lookups;
            var insertsAfterFirst = inserts;

            // 操作：もう一度求める
            var second = currentMemberIds.require();

            // 確認：同じ会員 ID で、リポジトリを呼んでいない
            assertThat(second).isEqualTo(first);
            assertThat(lookups).isEqualTo(lookupsAfterFirst);
            assertThat(inserts).isEqualTo(insertsAfterFirst);
        }

        @Test
        @DisplayName("DC-557-1 既にいた会員の 2 回目は、会員のリポジトリを呼ばない")
        void secondCallForExistingMemberSkipsRepository() {
            // 準備：会員がいて、ログインし、一度求めた
            var existing = UUID.randomUUID();
            members.put(SUBJECT, existing);
            loginAs(SUBJECT);
            assertThat(currentMemberIds.require()).isEqualTo(existing);
            var lookupsAfterFirst = lookups;

            // 操作：find で求める
            var second = currentMemberIds.find();

            // 確認：同じ会員 ID で、リポジトリを呼んでいない
            assertThat(second).contains(existing);
            assertThat(lookups).isEqualTo(lookupsAfterFirst);
            assertThat(inserts).isZero();
        }

        @Test
        @DisplayName("別のアカウントは、キャッシュにあっても別に探す")
        void otherSubjectIsLookedUp() {
            // 準備：1 つ目のアカウントで求めた
            loginAs(SUBJECT);
            var first = currentMemberIds.require();
            var lookupsAfterFirst = lookups;

            // 操作：別のアカウントで求める
            loginAs("subject-2");
            var second = currentMemberIds.require();

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
            // 準備：トランザクションの同期を有効にしてログインする
            TransactionSynchronizationManager.initSynchronization();
            loginAs(SUBJECT);

            // 操作：会員 ID を求める
            var id = currentMemberIds.require();

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
            // 準備：トランザクションの同期を有効にしてログインする
            TransactionSynchronizationManager.initSynchronization();
            loginAs(SUBJECT);

            // 操作：会員 ID を求め、ロールバックする
            currentMemberIds.require();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // 確認：キャッシュにない
            assertThat(cache.find(SUBJECT)).isEmpty();
        }
    }

    @Nested
    @DisplayName("ログインしていないとき")
    class Anonymous {

        @Test
        @DisplayName("require は、未認証（401）の業務のエラーで拒否される")
        void requireRejects() {
            // 操作と確認：業務のエラー UNAUTHENTICATED で拒否される
            assertBusinessError(currentMemberIds::require, CommonErrorCode.UNAUTHENTICATED);
        }

        @Test
        @DisplayName("find は空を返す")
        void findReturnsEmpty() {
            // 操作と確認：空
            assertThat(currentMemberIds.find()).isEmpty();
        }

        @Test
        @DisplayName("require と find は、会員を作らない")
        void createsNoMember() {
            // 操作：require（拒否される）と find を呼ぶ
            assertBusinessError(currentMemberIds::require, CommonErrorCode.UNAUTHENTICATED);
            currentMemberIds.find();

            // 確認：会員がない
            assertThat(members).isEmpty();
        }
    }

    @Nested
    @DisplayName("subject のない token でログインしているとき")
    class WithoutSubject {

        @Test
        @DisplayName("require と find は NullPointerException で失敗し、会員を作らない")
        void failsWithNullPointerAndCreatesNoMember() {
            // 準備：subject のない token でログインする
            loginWithoutSubject();

            // 操作と確認：どちらも NullPointerException（メッセージは確認しない）
            assertThatThrownBy(currentMemberIds::require).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(currentMemberIds::find).isInstanceOf(NullPointerException.class);

            // 確認：会員がない
            assertThat(members).isEmpty();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * subject のない token でログインしている状態にする。
     */
    private static void loginWithoutSubject() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").claim("scope", "none")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    /**
     * 会員の token でログインしている状態にする。
     *
     * @param subject IdP のアカウントの ID
     */
    private static void loginAs(String subject) {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
