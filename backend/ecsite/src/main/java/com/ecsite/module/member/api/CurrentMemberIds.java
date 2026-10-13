package com.ecsite.module.member.api;

import com.ecsite.common.security.CurrentMember;
import com.ecsite.module.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 要求した会員の会員 ID を求める（docs/backend/modules/member.md）。<br />
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
     * 会員のサービス
     */
    private final MemberService memberService;

    /**
     * ログインが要る操作の、会員 ID を返す（初めてなら会員を作る）。
     *
     * @return 会員 ID
     * @throws com.ecsite.common.error.exception.BusinessException ログインしていない（401）
     */
    public UUID require() {
        return memberService.memberIdOf(currentMember.require().subject());
    }

    /**
     * ログインが任意の操作の、会員 ID を返す（初めてなら会員を作る）。
     *
     * @return 会員 ID。ログインしていなければ空
     */
    public Optional<UUID> find() {
        return currentMember.find().map(member -> memberService.memberIdOf(member.subject()));
    }
}
