package com.ecsite.architecture.layerdeps.module.sample.application;

import com.ecsite.architecture.layerdeps.module.sample.api.ApiToApplication;

/**
 * 層の依存の検査の例：application から api を使う（表にない組なので規則に合わない）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class ApplicationToApi {

    /**
     * application が使う api のクラス
     */
    ApiToApplication api;
}
