package com.ecsite.module.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * IdP のアカウントの ID から会員 ID を求める。初めての会員なら会員を作る。
 *
 * <p>このクラスはトランザクションを持たない。キャッシュにあるときは、トランザクションを始めずに返す。
 * 参照と更新は、それぞれのサービスのトランザクションで行う。
 */
@Component
@RequiredArgsConstructor
public class MemberIdResolver {

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
     * IdP のアカウントの ID から会員 ID を求め、いなければ会員を作る。
     *
     * <p>見つけた、または作った会員 ID は、すぐにキャッシュに入れる。同じアカウントの 2 回目からは、キャッシュから返す。
     *
     * @param idpSubject IdP のアカウントの ID
     * @return 会員 ID
     */
    public UUID resolve(String idpSubject) {
        // キャッシュにあれば、トランザクションを始めずに返す
        var cached = cache.find(idpSubject);
        if (cached.isPresent()) {
            return cached.get();
        }
        // なければ、読み取り専用のトランザクションで探し、いなければ（初めてのリクエスト）作る
        var memberId = queryService.findMemberId(idpSubject).orElseGet(() -> commandService.create(idpSubject));
        cache.put(idpSubject, memberId);
        return memberId;
    }
}
