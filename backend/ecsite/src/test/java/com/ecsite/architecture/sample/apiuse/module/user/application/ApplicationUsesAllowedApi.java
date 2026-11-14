package com.ecsite.architecture.sample.apiuse.module.user.application;

import com.ecsite.architecture.sample.apiuse.module.sample.api.AllowedTargetApi;

/**
 * 他のモジュールの api の使い方の検査の例：application 層に見立てたクラスから、presentation 層だけに許した api の型を使う（規則に反する）
 *
 * <p>構成の検査が、許可の一覧の層の違いを見分けることを確認するための例であり、本番のコードではない。
 */
public class ApplicationUsesAllowedApi {

    /**
     * presentation 層だけに許した api の型
     */
    AllowedTargetApi allowedTargetApi;
}
