package com.ecsite.architecture.sample.apiuse.module.user.presentation;

import com.ecsite.architecture.sample.apiuse.module.sample.api.AllowedTargetApi;

/**
 * 他のモジュールの api の使い方の検査の例：presentation 層に見立てたクラスから、許可の一覧の api の型を使う（規則に合う）
 *
 * <p>構成の検査が、許可の一覧の使い方を違反にしないことを確認するための例であり、本番のコードではない。
 */
public class PresentationUsesAllowedApi {

    /**
     * presentation 層に許した api の型
     */
    AllowedTargetApi allowedTargetApi;
}
