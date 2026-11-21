package com.ecsite.module.member.application;

import com.ecsite.common.model.id.MemberId;
import com.ecsite.module.member.application.port.MemberRepository;
import com.ecsite.module.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 * <p>キャッシュの使い方と会員の作成を、Spring を起動せずに確認する。
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
    private final Map<String, MemberId> members = new HashMap<>();

    /**
     * 会員 ID を探す呼び出しの回数
     */
    private int lookups;

    /**
     * 会員を作る呼び出しの回数
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
        public Optional<MemberId> findIdByIdpSubject(String idpSubject) {
            lookups++;
            return Optional.ofNullable(members.get(idpSubject));
        }

        @Override
        public MemberId createOrGetId(Member member) {
            inserts++;
            return members.computeIfAbsent(member.idpSubject(), key -> member.id());
        }
    };

    /**
     * テストの対象
     */
    private final MemberIdResolver resolver = new MemberIdResolver(new MemberQueryService(repository),
            new MemberCommandService(repository, Clock.systemUTC(), UUID::randomUUID), cache);

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
            var existing = new MemberId(UUID.randomUUID());
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
        @DisplayName("別のアカウントは、他のアカウントがキャッシュにあっても、リポジトリで探す")
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
}
