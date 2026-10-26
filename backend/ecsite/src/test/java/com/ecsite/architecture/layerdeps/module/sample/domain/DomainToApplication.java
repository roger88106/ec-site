package com.ecsite.architecture.layerdeps.module.sample.domain;

import com.ecsite.architecture.layerdeps.module.sample.application.SampleService;

/**
 * 層の依存の検査の例：domain から application の service を使う（表にない組なので規則に合わない）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class DomainToApplication {

    /**
     * domain が使う service
     */
    SampleService service;
}
