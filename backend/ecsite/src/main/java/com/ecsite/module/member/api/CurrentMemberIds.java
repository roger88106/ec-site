package com.ecsite.module.member.api;

import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.application.MemberCommandService;
import com.ecsite.module.member.application.MemberQueryService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 要求した会員の会員 ID を求める（docs/design/modules/member.md）。<br />
 * API の入口が使う。IdP のアカウント（token の subject）から会員 ID を求める処理は、会員モジュールの中に閉じる。
 */
@Component
@RequiredArgsConstructor
public class CurrentMemberIds {

    /**
     * 要求の token の会員
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
     * ログインが必要な操作の、会員 ID を返す（初めてなら会員を作る）。
     *
     * @return 会員 ID
     * @throws com.ecsite.common.error.exception.BusinessException ログインしていない（401）
     */
    public UUID require() {
        return memberIdOf(currentMember.require().subject());
    }

    /**
     * ログインが任意の操作の、会員 ID を返す（初めてなら会員を作る）。
     *
     * @return 会員 ID。ログインしていなければ空
     */
    public Optional<UUID> find() {
        return currentMember.find().map(member -> memberIdOf(member.subject()));
    }

    /**
     * IdP のアカウントの ID から会員 ID を求める。<br />
     * ログインしたすべての要求で動くので、まず読み取り専用で探し、いなければ（初めての要求）作る。
     */
    private UUID memberIdOf(@Nullable String subject) {
        var idpSubject = Objects.requireNonNull(subject, "token に subject がある");
        return queryService.findMemberId(idpSubject).orElseGet(() -> commandService.create(idpSubject));
    }
}
