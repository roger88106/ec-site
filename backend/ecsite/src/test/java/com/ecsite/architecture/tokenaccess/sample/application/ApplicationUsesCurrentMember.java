package com.ecsite.architecture.tokenaccess.sample.application;

import com.ecsite.common.security.CurrentMember;

/**
 * token の取り出しの置き場所の検査の例：application 層に見立てたクラスから、会員の取り出しを使う（規則に合わない）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class ApplicationUsesCurrentMember {

    /**
     * application 層では使えない会員の取り出し
     */
    CurrentMember currentMember;
}
