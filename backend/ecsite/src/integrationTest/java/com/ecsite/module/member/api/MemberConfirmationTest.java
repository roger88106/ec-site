package com.ecsite.module.member.api;

import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import com.ecsite.support.async.Concurrently;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.jooq.member.Tables.MEMBER_;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 会員の確認の統合テスト
 *
 * <p>ログインしたリクエストの IdP のアカウントから、会員 ID を求めること（初回は会員を作る）と、そのときのトランザクションを確認する。
 * 会員の保存と検索は {@code JooqMemberRepositoryTest} が確認する。
 *
 * <p>初回のリクエストが同時に来る場合を確認するため、リクエストごとにコミットする。ロールバックしないが、作った会員は消さない。
 * IdP のアカウントの ID は、テストごとに新しくする。
 * トランザクションが読み取り専用かは、会員のリポジトリを包み、呼ばれたときの状態を記録して確認する。
 */
@IntegrationTest
class MemberConfirmationTest {

    // ============================== 定数 ==============================

    /**
     * 同時に送る初回のリクエストの数
     */
    private static final int CONCURRENT_REQUESTS = 8;

    // ============================== Bean ==============================

    @Autowired
    CurrentMemberIds currentMemberIds;

    @MockitoSpyBean
    MemberRepository memberRepository;

    @Autowired
    DSLContext dsl;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("初回のリクエスト")
    class FirstRequest {

        @Test
        @DisplayName("初めての IdP のアカウントなら、会員を 1 人作って、その会員 ID を返す")
        void createsMemberOnFirstRequest() {
            // 操作：初めてのアカウントで会員 ID を求める
            var subject = newSubject();
            var memberId = memberIdOf(subject);

            // 確認：会員 ID があり、このアカウントの会員が 1 人だけで、その ID
            assertThat(memberId).isNotNull();
            assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
            assertThat(dsl.select(MEMBER_.ID).from(MEMBER_).where(MEMBER_.IDP_SUBJECT.eq(subject)).fetchOne(MEMBER_.ID))
                    .isEqualTo(memberId);
        }

        @Test
        @DisplayName("同じアカウントの初回のリクエストが同時に来ても、会員は 1 人だけ作られ、全て同じ会員 ID になる")
        void createsOnlyOneMemberOnConcurrentFirstRequests() throws Exception {
            // 操作：同じ初めてのアカウントで、同時に会員 ID を求める
            var subject = newSubject();
            var memberIds = new HashSet<UUID>(Concurrently.run(CONCURRENT_REQUESTS, i -> {
                try {
                    return memberIdOf(subject);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));

            // 確認：会員 ID は 1 種類で、このアカウントの会員は 1 人だけ
            assertThat(memberIds).hasSize(1);
            assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("2 回目からのリクエスト")
    class LaterRequest {

        @Test
        @DisplayName("同じ IdP のアカウントは、同じ会員 ID になり、会員は 1 人のまま")
        void returnsSameMemberForSameAccount() {
            // 準備：会員を作る
            var subject = newSubject();
            var first = memberIdOf(subject);

            // 操作：同じアカウントで、もう一度求める
            var second = memberIdOf(subject);

            // 確認：同じ会員 ID で、このアカウントの会員は 1 人だけ
            assertThat(second).isEqualTo(first);
            assertThat(dsl.fetchCount(MEMBER_, MEMBER_.IDP_SUBJECT.eq(subject))).isEqualTo(1);
        }

        @Test
        @DisplayName("会員 ID は、IdP のアカウントの ID とは別の値")
        void memberIdDiffersFromSubject() {
            // 操作：会員 ID を求める
            var subject = newSubject();
            var memberId = memberIdOf(subject);

            // 確認：アカウントの ID をそのまま使わない
            assertThat(memberId.toString()).isNotEqualTo(subject);
        }

        @Test
        @DisplayName("別の IdP のアカウントは、別の会員になる")
        void returnsDifferentMembersForDifferentAccounts() {
            // 操作と確認：2 つのアカウントの会員 ID が違う
            assertThat(memberIdOf(newSubject())).isNotEqualTo(memberIdOf(newSubject()));
        }
    }

    @Nested
    @DisplayName("トランザクション")
    class Transaction {

        @Test
        @DisplayName("NFR-25 会員がいるときは、読み取り専用のトランザクションで探して、同じ会員 ID を返す")
        void findsExistingMemberReadOnly() {
            // 準備：キャッシュにない会員を DB に作り、作ったときの呼び出しは数えない
            var subject = newSubject();
            var memberId = insertMemberDirectly(subject);
            clearInvocations(memberRepository);
            var readOnly = recordReadOnly(subject);

            // 操作：そのアカウントで求める
            var found = memberIdOf(subject);

            // 確認：1 回だけ、読み取り専用のトランザクションで探し、同じ会員 ID
            assertThat(readOnly).containsExactly(true);
            assertThat(found).isEqualTo(memberId);
        }

        @Test
        @DisplayName("NFR-25 会員がいるときは、会員を作る操作を呼ばない")
        void doesNotInsertExistingMember() {
            // 準備：キャッシュにない会員を DB に作り、作ったときの呼び出しは数えない
            var subject = newSubject();
            insertMemberDirectly(subject);
            clearInvocations(memberRepository);

            // 操作：そのアカウントで求める
            memberIdOf(subject);

            // 確認：会員を作る操作を呼ばない
            verify(memberRepository, never()).insertIfAbsent(any());
        }

        @Test
        @DisplayName("NFR-25 会員がいないときは、まず読み取り専用のトランザクションで探す")
        void findsMissingMemberReadOnlyFirst() {
            // 準備：探したときのトランザクションを記録する
            var subject = newSubject();
            var readOnly = recordReadOnly(subject);

            // 操作：初めてのアカウントで会員 ID を求める
            memberIdOf(subject);

            // 確認：最初に探したのは、読み取り専用のトランザクション
            assertThat(readOnly.getFirst()).isTrue();
        }

        @Test
        @DisplayName("NFR-25 会員がいないときは、読み書きのトランザクションで会員を作る")
        void createsMissingMemberInReadWriteTransaction() {
            // 準備：会員を作ったときのトランザクションを記録する
            var subject = newSubject();
            var insertReadOnly = new CopyOnWriteArrayList<Boolean>();
            doAnswer(invocation -> {
                insertReadOnly.add(currentTransactionReadOnly());
                return invocation.callRealMethod();
            }).when(memberRepository).insertIfAbsent(any());

            // 操作：初めてのアカウントで会員 ID を求める
            memberIdOf(subject);

            // 確認：1 回だけ、読み取り専用でないトランザクションで作る
            assertThat(insertReadOnly).containsExactly(false);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * テストごとに新しい、IdP のアカウントの ID を作る。
     *
     * @return アカウントの ID
     */
    private static String newSubject() {
        return UUID.randomUUID().toString();
    }

    /**
     * 会員を、会員 ID のキャッシュを通さずに DB へ直接作る。
     *
     * @param subject IdP のアカウントの ID
     * @return 作った会員の会員 ID
     */
    private UUID insertMemberDirectly(String subject) {
        var id = UUID.randomUUID();
        memberRepository.insertIfAbsent(new Member(id, subject, Instant.now()));
        return id;
    }

    /**
     * IdP のアカウントでログインしたリクエストとして、会員 ID を求める。
     *
     * @param subject IdP のアカウントの ID
     * @return 会員 ID
     */
    private UUID memberIdOf(String subject) {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        return currentMemberIds.require();
    }

    /**
     * 会員を探す操作が呼ばれたときの、トランザクションが読み取り専用か（トランザクションがなければ null）を記録する。
     *
     * @param subject IdP のアカウントの ID
     * @return 記録（呼ばれるたびに増える）
     */
    private List<Boolean> recordReadOnly(String subject) {
        var readOnly = new CopyOnWriteArrayList<Boolean>();
        doAnswer(invocation -> {
            readOnly.add(currentTransactionReadOnly());
            return invocation.callRealMethod();
        }).when(memberRepository).findIdByIdpSubject(eq(subject));
        return readOnly;
    }

    /**
     * いまのトランザクションが読み取り専用かを返す。
     *
     * @return 読み取り専用なら true。トランザクションがなければ null
     */
    private static Boolean currentTransactionReadOnly() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                ? TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                : null;
    }
}
