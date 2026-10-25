package com.ecsite.architecture.commondeps.sample.common;

import com.ecsite.architecture.commondeps.sample.config.SampleConfig;

/**
 * 共通コードの依存の検査の例：共通コードに見立てたクラスから設定に見立てたクラスを使う（規則に合わない）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class CommonToConfig {

    /**
     * 共通コードが使う設定のクラス
     */
    SampleConfig config;
}
