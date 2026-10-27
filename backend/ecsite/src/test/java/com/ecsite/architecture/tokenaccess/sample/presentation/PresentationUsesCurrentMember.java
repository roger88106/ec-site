package com.ecsite.architecture.tokenaccess.sample.presentation;

import com.ecsite.common.security.CurrentMember;

/**
 * token の取り出しの置き場所の検査の例：presentation 層に見立てたクラスから、会員の取り出しを使う（規則に合う）。<br />
 * 構成の検査が、使ってよい場所を違反にしないことを確認するための例であり、本番のコードではない。
 */
public class PresentationUsesCurrentMember {

    /**
     * presentation 層で使える会員の取り出し
     */
    CurrentMember currentMember;
}
