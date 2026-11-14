package com.ecsite.architecture.sample.layerdeps.module.sample.api;

import com.ecsite.architecture.sample.layerdeps.module.sample.application.SampleService;

/**
 * 層の依存の検査の例：api から application の service を使う（表にある組なので規則に合う）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class ApiToApplication {

    /**
     * api が使う service
     */
    SampleService service;
}
