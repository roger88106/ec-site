package com.ecsite.module.member.api;

import com.ecsite.module.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 会員の API（docs/modules/member.md「他モジュールへの公開」）。
 */
@Component
@RequiredArgsConstructor
public class MemberApi {

    /**
     * 会員のサービス
     */
    private final MemberService memberService;

    /**
     * IdP のアカウントの ID から会員 ID を求める。会員がまだいなければ作る。<br />
     * 同じアカウントの初回の要求が同時に来ても、会員は 1 人だけ作る。
     *
     * @param idpSubject IdP のアカウントの ID（token の subject）
     * @return 会員 ID
     */
    public UUID memberIdOf(String idpSubject) {
        return memberService.memberIdOf(idpSubject);
    }
}
