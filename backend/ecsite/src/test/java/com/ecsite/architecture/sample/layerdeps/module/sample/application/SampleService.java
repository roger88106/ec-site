package com.ecsite.architecture.sample.layerdeps.module.sample.application;

import com.ecsite.architecture.sample.layerdeps.module.sample.domain.SampleDomain;

/**
 * 層の依存の検査の例：application の直下に置いた service
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class SampleService {

    /**
     * service が使う domain のクラス
     */
    SampleDomain domain;
}
