package com.ecsite.module.member.api;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.application.MemberCommandService;
import com.ecsite.module.member.application.MemberIdCache;
import com.ecsite.module.member.application.MemberQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

/**
 * リクエストした会員の会員 ID
 *
 * <p>IdP のアカウント（token の subject）から会員 ID を求める処理は、会員モジュールの中だけで行う。
 */
@Component
@RequiredArgsConstructor
public class CurrentMemberIds {

    /**
     * リクエストの token の会員
     */
    private final CurrentMember currentMember;

    /**
     * 会員の参照のサービス
     */
    private final MemberQueryService queryService;

    /**
     * 会員の更新のサービス
     */
    private final MemberCommandService commandService;

    /**
     * 会員 ID のキャッシュ
     */
    private final MemberIdCache cache;

    /**
     * ログインが必要な操作の、会員 ID を返し、初めての会員なら会員を作る。
     *
     * <p>同じアカウントの 2 回目からは、キャッシュから返す。
     *
     * @return 会員 ID
     * @throws BusinessException ログインしていない（{@code UNAUTHENTICATED}）
     */
    public UUID require() {
        return memberIdOf(currentMember.require().subject());
    }

    /**
     * ログインが任意の操作の会員 ID を返し、初めての会員なら会員を作る。
     *
     * @return 会員 ID。ログインしていなければ空
     */
    public Optional<UUID> find() {
        return currentMember.find().map(member -> memberIdOf(member.subject()));
    }

    /**
     * IdP のアカウントの ID から会員 ID を求める。
     */
    private UUID memberIdOf(String subject) {
        // キャッシュにあれば、トランザクションを始めずに返す
        var cached = cache.find(subject);
        if (cached.isPresent()) {
            return cached.get();
        }
        // なければ、読み取り専用のトランザクションで探し、いなければ（初めてのリクエスト）作る
        var memberId = queryService.findMemberId(subject).orElseGet(() -> commandService.create(subject));
        putAfterCommit(subject, memberId);
        return memberId;
    }

    /**
     * 会員 ID をキャッシュに入れる。
     *
     * <p>トランザクションの同期が有効なときは、確定の後に入れる。ロールバックしたテストで、消えた会員の ID が
     * キャッシュに残らないようにするため。本番では、トランザクションの外で呼ばれるので、すぐに入れる。
     */
    private void putAfterCommit(String subject, UUID memberId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.put(subject, memberId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.put(subject, memberId);
            }
        });
    }
}
