package com.ecsite.architecture.tokenaccess.sample.infrastructure.directory;

import com.ecsite.module.member.api.CurrentMemberIds;

/**
 * token の取り出しの置き場所の検査の例：infrastructure/directory/ に見立てたクラスから、会員 ID の取り出しを使う（規則に合わない）。<br />
 * directory を経て application 層が要求の会員 ID を得る形を、構成の検査が見つけられることを確認するための例であり、本番のコードではない。
 */
public class DirectoryUsesCurrentMemberIds {

    /**
     * presentation 層の外では使えない会員 ID の取り出し
     */
    CurrentMemberIds currentMemberIds;
}
