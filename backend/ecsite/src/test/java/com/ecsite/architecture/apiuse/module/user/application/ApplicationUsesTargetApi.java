package com.ecsite.architecture.apiuse.module.user.application;

import com.ecsite.architecture.apiuse.module.sample.api.TargetApi;

/**
 * 他のモジュールの api の使い方の検査の例：application 層に見立てたクラスから、許可の一覧にない api の型を使う（規則に反する）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class ApplicationUsesTargetApi {

    /**
     * 許可の一覧にない api の型
     */
    TargetApi targetApi;
}
